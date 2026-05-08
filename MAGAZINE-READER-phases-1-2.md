# Magazine Reader — Phases 1 & 2

Spec combinée du parser ePub magazine et de l'écran TOC paginé.
Calibrée sur 4 ePub réels : `2026-04-24-nzz`, `2026-04-25-24heures`,
`2026-04-25-courrier`, `2026-04-25-economist`.

---

## 0. Contexte producteur

Les 4 fichiers viennent du **même générateur**. Empreinte :

- `style.css` octet-pour-octet identique (md5 `3a2581ce190d2b7e5a8ac9f69c796aba`)
- Layout `OEBPS/` constant : `content.opf`, `style.css`, `title.xhtml`,
  `toc.xhtml`, `chapter-NN.xhtml`, `img-NNN.{jpg,png}`
- EPUB 3.0, `toc.xhtml` déclaré avec `properties="nav"` (pas de NCX)
- Spine systématique : `title-page → toc → ch-1 → ch-2 → …`
- Métadonnées OPF : `dc:title`, `dc:publisher`, `dc:date`, `dc:language`

**Conséquence pour l'implémentation** : on optimise pour ce producteur
(simple et fiable), avec des fallbacks pour ne pas planter sur un ePub voisin
légèrement différent.

### Tableau comparatif des fixtures

| ePub        | Lang | Articles | hero-img | lead    | Image dims        | Particularité                          |
|-------------|------|----------|----------|---------|-------------------|----------------------------------------|
| NZZ         | fr   | 22       | 22/22    | 22/22   | 200×200 (light)   | Couvertures déjà miniaturisées          |
| 24heures    | fr   | 9        | 0/9      | 9/9     | —                 | **Pas d'images du tout** (text-only)   |
| Courrier    | fr   | 6        | 6/6      | 6/6     | ~1200×630         | Images haute qualité                    |
| Economist   | en   | 73       | 72/73    | 69/73   | ~1080×1200        | 1 article utilise `img-container` au lieu de `hero-img` |

---

# Phase 1 — `MagazineParser`

## 1.1 Modèle de données (Kotlin)

```kotlin
data class MagazineIssue(
    val title: String,           // dc:title
    val publisher: String,       // dc:publisher
    val date: LocalDate,         // dc:date
    val language: String,        // dc:language
    val sections: List<MagazineSection>
)

data class MagazineSection(
    val name: String,            // ex: "Leaders", "Vaud et régions"
    val articles: List<MagazineArticle>
)

data class MagazineArticle(
    val spineIndex: Int,         // pour navigation prev/next
    val contentHref: String,     // ex: "chapter-12.xhtml"
    val title: String,           // depuis toc-title (autorité)
    val category: String,        // depuis toc-cat
    val author: String?,         // depuis toc-author (peut être vide)
    val lead: String?,           // depuis <p class="lead"> dans le chapitre
    val coverImageHref: String?  // ex: "img-001.jpg" ou null
)
```

## 1.2 Heuristique de détection « mode magazine »

Trois signatures, testées dans l'ordre. La première qui matche déclenche le mode magazine.

**Signature A — TOC structurée** (forte, attendue 100% du temps avec ce producteur) :
- Le fichier `nav` contient au moins un `<span class="toc-cat">` ET un `<span class="toc-title">`.

**Signature B — Contenu structuré** (médium, fallback) :
- Au moins 50% des chapitres du spine contiennent un `<p class="category">` ET un `<h1>`.

**Signature C — Sections inférées** (faible, future extension) :
- La nav contient une hiérarchie `<ol>` imbriquée avec headings de section.

Si aucune signature : `canParse = false`, fallback sur le reader standard.

## 1.3 Algorithme de parsing

```
1. Lire META-INF/container.xml → trouver le chemin de l'OPF
2. Parser l'OPF :
   - Extraire <metadata> (title, publisher, date, language)
   - Construire la table manifest (id → href, media-type)
   - Construire la liste spine (ordre des itemref)
   - Identifier le fichier nav (manifest item avec properties="nav")
3. Tester signatures A puis B → décider canParse
4. Si magazine :
   a. Parser nav.xhtml :
      - Pour chaque <li> dans <nav epub:type="toc"> > <ol> :
        - href ← <a>.href
        - title ← <span class="toc-title">.text
        - category ← <span class="toc-cat">.text
        - author ← <span class="toc-author">.text (trim, null si vide)
   b. Pour chaque article, ouvrir chapter-NN.xhtml et extraire :
      - lead ← premier <p class="lead">.text (peut être null)
      - coverImageHref ← cf. cascade ci-dessous
   c. Grouper en sections : parcourir les articles dans l'ordre du spine,
      démarrer une nouvelle section à chaque changement de category.
```

## 1.4 Cascade d'extraction de l'image de couverture

```kotlin
fun extractCoverImage(doc: Document): String? {
    // 1. Marqueur explicite (cas standard, 100/110 articles dans nos samples)
    doc.selectFirst("div.hero-img img")?.attr("src")?.let { return it }
    
    // 2. Variante observée (chapter-72 Economist : pages d'indicateurs)
    doc.selectFirst("div.img-container img")?.attr("src")?.let { return it }
    
    // 3. Fallback générique : premier <img> dans <body>, hors <blockquote>
    doc.select("body img").firstOrNull { img ->
        img.parents().none { it.tagName() == "blockquote" }
    }?.attr("src")?.let { return it }
    
    // 4. Pas d'image (cas 24heures) — la tuile devra avoir un fallback visuel
    return null
}
```

## 1.5 Résolution des chemins

Le manifest OPF utilise des hrefs relatifs au répertoire de l'OPF (ici `OEBPS/`).

```kotlin
// epubRoot = chemin du dossier extrait dans le cache app
// opfDir = "OEBPS" (parsé depuis container.xml)
fun resolveAsset(epubRoot: File, opfDir: String, href: String): File =
    File(epubRoot, "$opfDir/$href")
```

## 1.6 Fixtures de test

Les 4 ePubs sont placés dans `app/src/test/resources/fixtures/`. Pour chacun, un
fichier `expected.json` capture la sortie attendue du parser.

**Exemple `2026-04-25-24heures.expected.json`** (extrait) :
```json
{
  "title": "24 heures — Edition du samedi 25 avril 2026",
  "publisher": "Tamedia / TX Group",
  "date": "2026-04-25",
  "language": "fr",
  "sections": [
    {
      "name": "Point fort",
      "articles": [
        {
          "spineIndex": 2,
          "contentHref": "chapter-01.xhtml",
          "title": "A Tchernobyl, quarante ans après: «Nous vivons sous une menace constante»",
          "category": "Point fort",
          "author": "Joseph Roche et Iryna Matviyishyn...",
          "lead": "Reportage Le 26 avril 1986...",
          "coverImageHref": null
        }
      ]
    },
    {
      "name": "Vaud et régions",
      "articles": [ /* chapters 02 et 03 groupés */ ]
    }
  ]
}
```

## 1.7 Critères d'acceptation Phase 1

- [ ] Les 4 fixtures parsent sans exception
- [ ] Pour chaque article, `title` et `category` non-null et non-vides
- [ ] NZZ : 22/22 articles ont `coverImageHref` non-null
- [ ] Courrier : 6/6 articles ont `coverImageHref` non-null
- [ ] Economist : ≥ 72/73 articles ont `coverImageHref` non-null
- [ ] 24heures : 9/9 articles ont `coverImageHref = null` (sans erreur)
- [ ] Le regroupement en sections est correct (ex. Economist a une section
      "Leaders" avec 5 articles consécutifs)
- [ ] Un ePub littéraire standard (sans `toc-cat` dans nav) retourne `canParse = false`
- [ ] Tests unitaires couvrent : parser principal, cascade image, détection
      magazine, grouping sections
- [ ] Tous les hrefs résolus correctement (relatifs au répertoire OPF)

## 1.8 Edge cases identifiés

- `toc-author` peut être vide (ex. Economist Leaders, éditoriaux non signés)
  → `author = null`
- `lead` peut être absent (4 articles Economist sur 73) → `lead = null`
- 24heures ne ship aucune image → toute la cascade renvoie null, c'est OK
- Drop-cap encodé bizarrement dans certains chapitres Economist
  (`<span class="drop-cap"><</span>b>L</b>`) — n'affecte pas le parser, juste
  le rendu (à régler en Phase 3)
- Title-page et toc.xhtml sont dans le spine mais ne sont PAS des articles
  → les ignorer (filtrer sur les itemref qui ne sont pas `title-page` ni `toc`)

---

# Phase 2 — `MagazineTocScreen`

## 2.1 Objectif

Afficher la TOC d'un numéro de magazine sous forme de **liste verticale d'items
horizontaux paginés**, où chaque item montre `catégorie + titre + image`. La
pagination est calculée dynamiquement selon la taille d'écran : pas de scroll,
on tourne les pages comme dans le reader.

## 2.2 Référence visuelle

Inspiration : la page d'accueil RTS. Layout :

```
┌──────────────────────────────────────────┐
│  NZZ — vendredi 24 avril 2026            │  ← en-tête fin (32-40dp)
├──────────────────────────────────────────┤
│                                          │
│  International                  ┌──────┐ │
│  De retour de captivité russe : │      │ │  ← carte article (140dp)
│  larmes de joie...              │ img  │ │
│                                 └──────┘ │
│  ───────────────────────────────────────│  ← séparateur
│                                          │
│  Économie                       ┌──────┐ │
│  L'inflation revient...         │ img  │ │
│                                 └──────┘ │
│  ───────────────────────────────────────│
│                                          │
│  Culture                                 │  ← carte sans image
│  Le théâtre vaudois en crise             │     (texte pleine largeur)
│  ───────────────────────────────────────│
│                                          │
│             ◦ ◦ • ◦ ◦  Page 3 / 5         │  ← indicateur de page
└──────────────────────────────────────────┘
```

## 2.3 Composants

### `ArticleCard`
Carte horizontale d'un article. Hauteur fixe (`CARD_HEIGHT = 140.dp`).

```kotlin
@Composable
fun ArticleCard(
    article: MagazineArticle,
    epubAssetResolver: (String) -> Any,  // résout coverImageHref → File/Uri
    isActive: Boolean,                    // article courant en lecture
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .clickable(onClick = onClick)
            .then(if (isActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Text(
                text = article.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = SerifFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (article.coverImageHref != null) {
            AsyncImage(
                model = epubAssetResolver(article.coverImageHref),
                contentDescription = null,
                modifier = Modifier
                    .size(CARD_HEIGHT - 24.dp)  // carré, légèrement plus petit que la carte
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
```

### `IssueHeader`
En-tête fin avec titre du numéro. Hauteur `HEADER_HEIGHT = 40.dp`.

### `PageIndicator`
Indicateur en bas : `"Page X / Y"` + dots discrets. Hauteur `INDICATOR_HEIGHT = 32.dp`.

## 2.4 Algorithme de pagination dynamique

Le cœur technique. Calcul des items par page basé sur l'espace disponible.

```kotlin
@Composable
fun MagazineTocScreen(issue: MagazineIssue, /* ... */) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        
        // Constantes de layout
        val CARD_HEIGHT = 140.dp
        val SEPARATOR_HEIGHT = 1.dp
        val HEADER_HEIGHT = 40.dp
        val INDICATOR_HEIGHT = 32.dp
        val VERTICAL_PADDING = 8.dp  // entre header/contenu/indicator
        
        // Espace disponible pour les cartes (hors header et indicator)
        val cardsAreaHeight = maxHeight - HEADER_HEIGHT - INDICATOR_HEIGHT - (VERTICAL_PADDING * 2)
        val itemPlusSeparator = CARD_HEIGHT + SEPARATOR_HEIGHT
        
        // Items par page
        val itemsPerPage = (cardsAreaHeight / itemPlusSeparator).toInt().coerceAtLeast(1)
        
        // Liste plate de tous les articles (les sections ne sont pas affichées
        // séparément ; la catégorie figure sur chaque carte)
        val allArticles = remember(issue) { issue.sections.flatMap { it.articles } }
        val pages = remember(allArticles, itemsPerPage) { allArticles.chunked(itemsPerPage) }
        
        // Pager state, initialisé sur la page contenant l'article actif si applicable
        val activeArticleHref = /* depuis ViewModel */ null
        val initialPage = remember(pages, activeArticleHref) {
            if (activeArticleHref == null) 0
            else pages.indexOfFirst { page -> page.any { it.contentHref == activeArticleHref } }
                .coerceAtLeast(0)
        }
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
        
        Column(modifier = Modifier.fillMaxSize()) {
            IssueHeader(issue = issue, modifier = Modifier.height(HEADER_HEIGHT))
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false  // navigation par tap zones uniquement
            ) { pageIdx ->
                Column {
                    pages[pageIdx].forEachIndexed { idx, article ->
                        ArticleCard(
                            article = article,
                            isActive = article.contentHref == activeArticleHref,
                            onClick = { /* navigateToReader(article) */ }
                        )
                        if (idx < pages[pageIdx].lastIndex) {
                            HorizontalDivider(thickness = SEPARATOR_HEIGHT)
                        }
                    }
                }
            }
            
            PageIndicator(
                current = pagerState.currentPage + 1,
                total = pages.size,
                modifier = Modifier.height(INDICATOR_HEIGHT)
            )
        }
    }
}
```

## 2.5 Navigation page (turn page, pas swipe)

Comme `userScrollEnabled = false` sur le pager, on superpose des zones tactiles
invisibles côté gauche (page précédente) et côté droite (page suivante). Pas
d'animation, ou animation ultra-courte (≤100ms) pour cohérence avec le mode e-ink.

```kotlin
// À placer dans le contenu de chaque page du pager, en overlay :
Row(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null  // pas de ripple
            ) { 
                scope.launch { 
                    if (pagerState.currentPage > 0)
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                } 
            }
    )
    Spacer(modifier = Modifier.weight(1f))  // zone neutre centrale
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(/* même config */) { 
                scope.launch {
                    if (pagerState.currentPage < pages.size - 1)
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
    )
}
```

À noter : les overlays doivent être **derrière** les cartes (z-order), sinon les
taps sur les cartes ne passeront pas. À régler en mettant les zones tactiles en
première position dans la `Box` et les cartes au-dessus.

## 2.6 Détection magazine et routage

Au moment de l'ouverture d'un livre :

```kotlin
class BookOpenViewModel(/* ... */) {
    fun open(file: File) {
        val epub = EpubFile.load(file)
        val parser = MagazineParser()
        if (parser.canParse(epub)) {
            val issue = parser.parse(epub)
            navigator.navigate(Route.MagazineToc(issue))
        } else {
            // Fallback sur le reader standard de Book's Story
            navigator.navigate(Route.StandardReader(file))
        }
    }
}
```

## 2.7 Asset resolver (chargement images depuis le ZIP)

Pour éviter d'extraire tout l'ePub sur disque, créer un `EpubAssetFetcher` Coil
custom qui lit les images directement depuis le ZIP :

```kotlin
class EpubAssetFetcher(
    private val epubFile: File,
    private val opfDir: String
) : Fetcher {
    override suspend fun fetch(href: String): FetchResult {
        ZipFile(epubFile).use { zip ->
            val entry = zip.getEntry("$opfDir/$href") ?: error("Asset not found: $href")
            val bytes = zip.getInputStream(entry).readBytes()
            return SourceResult(
                source = ImageSource(Buffer().write(bytes), context),
                mimeType = guessMimeType(href),
                dataSource = DataSource.DISK
            )
        }
    }
}
```

À enregistrer dans le `ImageLoader` global. **Alternative plus simple** si Book's
Story extrait déjà l'ePub dans son cache : passer un `File` directement à
`AsyncImage`. À vérifier dans le code existant avant de coder le fetcher custom.

## 2.8 Persistence de la position

Étendre la table `book_progress` (ou équivalent dans Book's Story) :

```kotlin
@Entity
data class BookProgress(
    val bookId: String,
    val currentArticleHref: String?,    // nouveau, null si TOC ou livre standard
    val currentScrollOrPosition: Float,  // position dans l'article courant (CFI ou %)
    val lastUpdated: Instant
)
```

Quand l'utilisateur :
- Ouvre un article depuis la TOC → `currentArticleHref = article.contentHref`
- Tape sur la zone TOC depuis le reader → on revient à `MagazineTocScreen`,
  qui s'initialise sur la page contenant `currentArticleHref`, avec cette
  carte en évidence.

## 2.9 Polices

`SerifFontFamily` à placer dans le module ressources :
- Choix par défaut recommandé : **Source Serif 4** (open-source, excellente
  lisibilité, familiale large)
- Alternatives : **Lora**, **EB Garamond**, **Crimson Pro**
- Charger depuis `app/src/main/res/font/` via `Font(R.font.source_serif_4_regular)` etc.
- Préférence utilisateur : exposer un `FontFamilyChoice` dans les settings (Phase 6)

## 2.10 Critères d'acceptation Phase 2

- [ ] `MagazineTocScreen` s'affiche correctement pour les 4 fixtures
- [ ] Calcul `itemsPerPage` correct sur 3 tailles d'écran simulées :
      360×640 (compact phone), 480×800 (medium), 800×1280 (tablet/Boox-like)
- [ ] Pas de scroll vertical possible dans le pager (`userScrollEnabled = false`)
- [ ] Tap zone gauche → page précédente ; tap zone droite → page suivante
- [ ] Tap sur une carte → ouvre le reader (stub OK pour Phase 2, branchera
      vraiment en Phase 3)
- [ ] Carte sans image (cas 24heures) → texte pleine largeur, layout propre
- [ ] Carte active (selon `currentArticleHref`) a un border visible
- [ ] `initialPage` du pager pointe sur la page contenant l'article actif au retour
- [ ] Tests Compose UI : snapshot des 4 fixtures sur 360×640 et 800×1280
- [ ] Aucune dépendance ajoutée hors écosystème Compose / Coil / déjà présent

## 2.11 Edge cases UI

- Numéro avec un seul article (théoriquement possible) → 1 seule page, pas
  d'indicator visible
- Numéro avec beaucoup d'articles très longs en titre → ellipsis sur 3 lignes
- Très petit écran qui ne peut afficher qu'1 article par page → fonctionne,
  juste plus de pages
- Très grand écran (tablette) où la carte 140dp paraîtrait écrasée → on pourrait
  passer à `CARD_HEIGHT = 160.dp` au-delà d'un seuil (`maxWidth > 600.dp`),
  mais à valider visuellement avant

---

# Prompts Claude Code

À utiliser séquentiellement, un PR par phase.

## Prompt Phase 1

> Je veux ajouter un `MagazineParser` dans le module qui gère le parsing ePub
> (probablement `core/parser` ou équivalent — confirme avant de modifier).
> 
> Lis ce document à la racine du repo. Implémente exactement les data classes,
> l'interface, l'heuristique de détection et la cascade d'extraction décrites
> en section "Phase 1". Utilise jsoup pour le parsing XHTML (déjà présent dans
> les dépendances Book's Story).
> 
> Les 4 ePub fixtures sont dans `app/src/test/resources/fixtures/` avec leur
> `*.expected.json` à côté. Écris des tests JUnit qui :
>   1. Pour chaque fixture, parsent et comparent le résultat au JSON attendu
>   2. Vérifient `canParse() == false` sur un ePub fiction standard (utiliser
>      n'importe quel ePub Project Gutenberg comme contre-exemple)
>   3. Couvrent chaque branche de la cascade d'extraction d'image
> 
> Ne modifie RIEN d'autre que :
>   - Les nouveaux fichiers du parser
>   - Les nouveaux tests et fixtures
>   - Si nécessaire, l'enregistrement du parser dans le registre existant
> 
> À la fin, produis un PR/diff isolé avec une description courte de ce qui
> a été ajouté et pourquoi. N'intègre pas encore le parser dans l'UI — ça
> sera la Phase 2.

## Prompt Phase 2

> Implémente la Phase 2 selon ce document. Le `MagazineParser` de la Phase 1
> est déjà en place et testé.
>
> Étapes attendues :
>   1. Crée un nouveau module ou package `feature/magazine-toc` (à toi de
>      voir la convention dans le repo existant)
>   2. Implémente `ArticleCard`, `IssueHeader`, `PageIndicator`, `MagazineTocScreen`
>      selon les composables fournis dans le spec
>   3. Vérifie d'abord si Book's Story extrait déjà les ePubs dans son cache.
>      Si oui, charge les images via `File` direct dans Coil. Sinon, implémente
>      `EpubAssetFetcher` et enregistre-le dans le `ImageLoader` global
>   4. Ajoute le routage : à l'ouverture d'un livre, si `MagazineParser.canParse()`,
>      route vers `MagazineTocScreen` ; sinon, fallback inchangé
>   5. Étends la persistance pour stocker `currentArticleHref`
>   6. Tests Compose UI : snapshot des 4 fixtures sur 360×640 et 800×1280
>   7. Pour cette phase, le tap sur une carte affiche un Toast avec le titre
>      (pas de vrai reader, ce sera la Phase 3)
>
> Ne touche pas au reader existant. La détection magazine doit être additive :
> un ePub fiction standard doit continuer à s'ouvrir comme avant.
>
> Produis un PR/diff isolé avec les changements et un screenshot de chaque
> fixture sur les deux tailles d'écran.
