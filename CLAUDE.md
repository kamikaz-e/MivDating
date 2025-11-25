# CLAUDE.md - MivDating RAG Application

## Project Overview

**MivDating** is an Android application implementing a RAG (Retrieval Augmented Generation) system using Ollama embeddings for semantic document search. The app parses documents (TXT/HTML), chunks them intelligently, generates embeddings using Ollama, stores them in a local SQLite vector database, and enables semantic similarity search.

**Primary Language:** Kotlin
**UI Framework:** Jetpack Compose
**Architecture:** MVVM (Model-View-ViewModel)
**Minimum SDK:** 26 (Android 8.0)
**Target SDK:** 36
**Build System:** Gradle with Kotlin DSL

---

## Project Structure

```
MivDating/
├── app/
│   ├── build.gradle.kts           # App-level Gradle configuration
│   ├── proguard-rules.pro         # ProGuard rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                # Document files for indexing
│       │   ├── book1.txt
│       │   └── book2.html
│       ├── kotlin/dev/kamikaze/mivdating/
│       │   ├── MainActivity.kt              # Entry point activity
│       │   ├── OllamaViewModel.kt          # Main ViewModel (RAGViewModel)
│       │   ├── ui/
│       │   │   └── OllamaRAGScreen.kt      # Main Compose UI screen
│       │   └── data/
│       │       ├── chunking/
│       │       │   └── ChunkingConfig.kt    # Text chunking logic & config
│       │       ├── indexing/
│       │       │   └── IndexingProgress.kt  # Indexing service & progress states
│       │       ├── models/
│       │       │   ├── Document.kt          # Core data models
│       │       │   ├── OllamaEmbeddingsRequest.kt
│       │       │   └── OllamaEmbeddingsResponse.kt
│       │       ├── network/
│       │       │   └── OllamaClient.kt      # HTTP client for Ollama API
│       │       ├── parser/
│       │       │   └── DocumentParser.kt    # TXT/HTML document parser
│       │       └── storage/
│       │           ├── VectorDatabase.kt    # SQLite-based vector store
│       │           └── JsonVectorStore.kt   # (Alternative JSON storage)
│       └── res/                   # Android resources (layouts, themes, etc.)
├── build.gradle.kts               # Project-level Gradle configuration
├── settings.gradle.kts            # Gradle settings
├── gradle/libs.versions.toml      # Dependency version catalog
├── start-ollama.sh                # Script to start Ollama server
└── .gitignore
```

---

## Core Architecture

### MVVM Pattern

The app follows a clean MVVM architecture:

1. **View Layer** (`ui/`)
   - `OllamaRAGScreen.kt`: Main composable UI with document indexing, search interface, and results display
   - Pure declarative UI using Jetpack Compose
   - Observes ViewModel state via `StateFlow`

2. **ViewModel Layer**
   - `RAGViewModel` (in `OllamaViewModel.kt`): Manages UI state, orchestrates data operations
   - Exposes `RAGUiState` via `StateFlow` for reactive UI updates
   - Handles user actions (indexing, search, clear)
   - Uses `viewModelScope` for coroutine-based async operations

3. **Data Layer** (`data/`)
   - **Chunking**: `TextChunker` splits documents into semantically meaningful chunks
   - **Indexing**: `IndexingService` orchestrates parsing → chunking → embedding → storage
   - **Network**: `OllamaClient` communicates with Ollama API for embeddings
   - **Storage**: `VectorDatabase` manages SQLite operations for documents and embeddings
   - **Parsing**: `DocumentParser` extracts text from TXT and HTML files

### Data Flow

```
User Action (Index/Search)
    ↓
RAGViewModel
    ↓
IndexingService / Search
    ↓
DocumentParser → TextChunker → OllamaClient → VectorDatabase
    ↓
UI State Update (Flow emissions)
    ↓
OllamaRAGScreen (recompose)
```

---

## Key Components

### 1. Document Parsing (`DocumentParser.kt`)

**Location:** `app/src/main/kotlin/dev/kamikaze/mivdating/data/parser/DocumentParser.kt`

- Reads files from `assets/` directory
- Supports:
  - `.txt` files: raw text extraction
  - `.html` files: Jsoup-based HTML parsing (removes scripts, styles, nav, footer, header)
- Generates deterministic document IDs using UUID from source path
- Returns `Pair<Document, String>` (metadata + extracted text)

**Conventions:**
- Always use `assets/` prefix for source paths
- Document ID is UUID v5 based on source path (deterministic)

### 2. Text Chunking (`ChunkingConfig.kt`)

**Location:** `app/src/main/kotlin/dev/kamikaze/mivdating/data/chunking/ChunkingConfig.kt`

Implements intelligent text segmentation with:

**Default Configuration:**
- `chunkSize`: 512 characters
- `chunkOverlap`: 128 characters (preserves context across chunks)
- `minChunkSize`: 100 characters
- `separators`: `["\n\n", "\n", ". ", "! ", "? ", " "]` (priority order)

**Chunking Strategy:**
- Finds optimal split points using separator hierarchy
- Maintains semantic boundaries (prefers sentence/paragraph breaks)
- Ensures overlap for context preservation
- Alternative method: `chunkBySentences()` for sentence-based chunking

**Conventions:**
- Chunk IDs are deterministic: `UUID(documentId:chunk:index)`
- Always clean text (normalize whitespace) before chunking

### 3. Ollama Integration (`OllamaClient.kt`)

**Location:** `app/src/main/kotlin/dev/kamikaze/mivdating/data/network/OllamaClient.kt`

- **HTTP Client:** Ktor with Android engine
- **Base URL:** `http://10.0.2.2:11434` (Android emulator localhost)
- **Model:** `nomic-embed-text` (configurable)
- **Timeouts:**
  - Request: 60s
  - Connect: 15s
  - Socket: 60s

**API Methods:**
- `embed(text: String)`: Single text embedding
- `embedBatch(texts: List<String>)`: Batch embeddings (sequential)
- `isAvailable()`: Health check

**Important Notes:**
- Ollama must be running on host machine with `OLLAMA_HOST=0.0.0.0:11434`
- Use `start-ollama.sh` to configure and start Ollama
- Embeddings are `List<Double>` (dimension depends on model)

### 4. Vector Database (`VectorDatabase.kt`)

**Location:** `app/src/main/kotlin/dev/kamikaze/mivdating/data/storage/VectorDatabase.kt`

SQLite-based vector storage with two tables:

**Schema:**
```sql
-- Documents table
CREATE TABLE documents (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    source TEXT NOT NULL,
    type TEXT NOT NULL  -- 'TXT' or 'HTML'
)

-- Embeddings table
CREATE TABLE embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chunk_id TEXT UNIQUE NOT NULL,
    document_id TEXT NOT NULL,
    content TEXT NOT NULL,
    vector TEXT NOT NULL,  -- JSON-serialized List<Double>
    FOREIGN KEY (document_id) REFERENCES documents(id)
)
CREATE INDEX idx_doc_id ON embeddings(document_id)
```

**Key Operations:**
- `insertDocument()`: Upsert document (CONFLICT_IGNORE)
- `insertEmbeddings()`: Batch insert with transaction
- `searchSimilar()`: Cosine similarity search across all embeddings
- `documentExistsById()`: Check for duplicate documents
- `clearAll()`: Wipe entire index

**Search Algorithm:**
- Loads all embeddings into memory
- Computes cosine similarity: `dot(a,b) / (||a|| * ||b||)`
- Returns top-K results sorted by similarity score

**Conventions:**
- Embeddings stored as JSON strings (not efficient for large-scale, but simple)
- Always use transactions for batch operations
- Document existence checked by ID before indexing

### 5. Indexing Service (`IndexingProgress.kt`)

**Location:** `app/src/main/kotlin/dev/kamikaze/mivdating/data/indexing/IndexingProgress.kt`

Orchestrates the full indexing pipeline with progress tracking:

**Pipeline Stages:**
1. `Parsing`: Read and parse document from assets
2. `Chunking`: Split text into chunks
3. `Embedding`: Generate embeddings (emits progress per chunk)
4. `Saving`: Store embeddings in database
5. `Completed`: Final stats or `Error`

**Flow-Based Progress:**
```kotlin
indexingService.indexDocuments(files).collect { progress ->
    when (progress) {
        is IndexingProgress.Parsing -> // Update UI
        is IndexingProgress.Embedding -> // Show progress bar
        is IndexingProgress.Completed -> // Show results
        // ...
    }
}
```

**Important Behaviors:**
- Skips already-indexed documents (checks by document ID)
- Sequential processing (documents and chunks)
- Uses `Dispatchers.IO` for blocking operations
- Returns actual counts from database on completion

### 6. ViewModel State (`OllamaViewModel.kt`)

**Location:** `app/src/main/kotlin/dev/kamikaze/mivdating/OllamaViewModel.kt`

**UI State Structure:**
```kotlin
data class RAGUiState(
    val isIndexing: Boolean = false,
    val isSearching: Boolean = false,
    val progress: String = "",
    val progressPercent: Float = 0f,
    val documentsCount: Int = 0,
    val chunksCount: Int = 0,
    val documents: List<Document> = emptyList(),
    val searchResults: List<SearchResult> = emptyList(),
    val error: String? = null,
    val ollamaAvailable: Boolean = false
)
```

**Key Methods:**
- `indexBooks()`: Starts indexing for `book1.txt` and `book2.html`
- `search()`: Performs semantic search with current query
- `clearIndex()`: Wipes all data from database
- `checkOllamaConnection()`: Verifies Ollama availability on init

**Lifecycle:**
- Auto-checks Ollama connection on init
- Loads stats (doc/chunk counts) on init
- Closes clients on `onCleared()`

---

## Development Workflows

### Adding New Documents

1. Place document in `app/src/main/assets/`
2. Update `indexBooks()` in `RAGViewModel.kt`:
   ```kotlin
   val files = listOf("book1.txt", "book2.html", "newdoc.pdf")  // Add here
   ```
3. Rebuild and run the app
4. Click "Индексировать книги" button

**Note:** Only TXT and HTML supported currently. For PDF, implement parser in `DocumentParser.kt`.

### Changing Chunking Strategy

Edit `ChunkingConfig` in `RAGViewModel.kt`:

```kotlin
private val indexingService = IndexingService(
    // ...
    chunkingConfig = ChunkingConfig(
        chunkSize = 1024,       // Larger chunks
        chunkOverlap = 256,     // More overlap
        minChunkSize = 200
    )
)
```

**Important:** Clear index after changes to avoid mixing chunk sizes.

### Using Different Ollama Models

Update `OllamaClient` initialization:

```kotlin
private val ollamaClient = OllamaClient(
    embeddingModel = "all-minilm"  // Change model here
)
```

**Prerequisites:**
- Model must be pulled on host: `ollama pull all-minilm`
- Restart Ollama server
- Clear and re-index documents (embeddings are model-specific)

### Testing Ollama Connection

1. Start Ollama with `./start-ollama.sh`
2. Verify in app: Green "🟢 Ollama подключен" indicator
3. If red, check:
   - Ollama is running on host
   - `OLLAMA_HOST=0.0.0.0:11434` is set
   - Firewall allows port 11434
   - Emulator can reach `10.0.2.2` (host loopback)

### Building for Release

1. Update version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2
   versionName = "1.1.0"
   ```
2. Build release APK:
   ```bash
   ./gradlew assembleRelease
   ```
3. Output: `app/build/outputs/apk/release/app-release.apk`

**Note:** ProGuard is enabled for release builds (see `proguard-rules.pro`).

---

## Code Conventions

### Kotlin Style

- **Indentation:** 4 spaces
- **Line length:** No hard limit, but prefer readability
- **Naming:**
  - Classes/Objects: `PascalCase`
  - Functions/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Private properties: prefix with `_` for StateFlow backing fields

### Compose Patterns

- **State hoisting:** ViewModels own state, composables are stateless
- **Reusability:** Extract complex UI into separate `@Composable` functions
- **Naming:** Composables are nouns (e.g., `SearchSection`, `IndexStats`)
- **Modifiers:** Always pass `Modifier` as first optional parameter

### Coroutines

- Use `viewModelScope` for ViewModel operations
- Use `Dispatchers.IO` for:
  - Database operations
  - File I/O
  - Network requests (Ktor handles internally, but explicit is fine)
- Use `Flow` for progress/streaming operations
- Prefer `suspend` functions over callbacks

### Error Handling

- **Network/IO errors:** Wrap in try-catch, update `error` state
- **User-facing errors:** Russian language messages (match existing style)
- **Logging:** Currently minimal; use Android Logcat for debugging
- **Recovery:** Always provide way to retry or clear state

### Database

- **Transactions:** Use for batch operations (`insertEmbeddings`)
- **Cursors:** Always use `.use {}` for auto-closing
- **Migrations:** Not implemented; `onUpgrade` drops and recreates (OK for local cache)
- **Conflict resolution:**
  - Documents: `CONFLICT_IGNORE` (skip duplicates)
  - Embeddings: `CONFLICT_REPLACE` (update if exists)

### File Organization

- **Package structure:**
  - `ui/`: All composables
  - `data/`: All data-related code, subdivided by concern
  - `di/`: Dependency injection (Hilt modules) - currently unused
- **File naming:** Match primary class name
- **Single responsibility:** One main class per file (helpers OK)

---

## Dependencies

### Core Android

- `androidx.core:core-ktx` - Kotlin extensions
- `androidx.lifecycle:lifecycle-runtime-ktx` - Lifecycle + Coroutines
- `androidx.activity:activity-compose` - Compose integration

### Jetpack Compose

- `androidx.compose.bom` - BOM for version alignment
- `androidx.compose.ui:*` - UI toolkit
- `androidx.compose.material3:material3` - Material Design 3
- `androidx.compose.material:material-icons-extended` - Icon set
- `androidx.lifecycle:lifecycle-viewmodel-compose` - ViewModel integration
- `androidx.navigation:navigation-compose` - Navigation (currently unused)

### Dependency Injection

- `com.google.dagger:hilt-android` - Hilt DI (configured but not actively used)
- `androidx.hilt:hilt-navigation-compose` - Hilt + Compose integration

### Networking

- `io.ktor:ktor-client-*` - HTTP client with JSON support
  - `ktor-client-core`, `ktor-client-android` (engine)
  - `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`
  - `ktor-client-logging`, `ktor-client-auth`

### Serialization

- `org.jetbrains.kotlinx:kotlinx-serialization-json` - JSON serialization

### Utilities

- `org.jsoup:jsoup` - HTML parsing
- `com.jakewharton.timber:timber` - Logging (not currently used)
- `androidx.datastore:datastore-preferences` - DataStore (not currently used)
- `io.coil-kt:coil-compose` - Image loading (not currently used)

### Testing

- `junit:junit` - Unit tests
- `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core` - Instrumented tests
- `androidx.compose.ui:ui-test-junit4` - Compose UI tests

---

## Common Tasks for AI Assistants

### Task: Add Support for PDF Documents

1. Add PDF parsing library to `gradle/libs.versions.toml` and `app/build.gradle.kts`
2. Update `DocumentType` enum in `data/models/Document.kt`:
   ```kotlin
   enum class DocumentType { TXT, HTML, PDF }
   ```
3. Implement PDF parsing in `DocumentParser.kt`:
   ```kotlin
   DocumentType.PDF -> parsePdf(content)
   ```
4. Test with sample PDF in `assets/`

### Task: Implement Batch Embedding Optimization

Currently `embedBatch()` calls `embed()` sequentially. To optimize:

1. Update `OllamaClient.embedBatch()` to use parallel coroutines:
   ```kotlin
   suspend fun embedBatch(texts: List<String>): List<List<Double>> {
       return coroutineScope {
           texts.map { text -> async { embed(text) } }.awaitAll()
       }
   }
   ```
2. Update `IndexingService.indexDocuments()` to use batch embedding
3. Test with large document sets

### Task: Add Filtering by Document

1. Add `selectedDocumentId: String?` to `RAGUiState`
2. Add document filter dropdown in `SearchSection`
3. Update `VectorDatabase.searchSimilar()` to accept optional `documentId` filter:
   ```kotlin
   fun searchSimilar(
       queryEmbedding: List<Double>,
       topK: Int = 5,
       documentId: String? = null
   ): List<SearchResult>
   ```
4. Filter results in SQL query or in-memory

### Task: Implement Caching for Search Results

1. Add `cachedResults: Map<String, List<SearchResult>>` to ViewModel
2. Before searching, check cache:
   ```kotlin
   val cached = cachedResults[searchQuery]
   if (cached != null) {
       _uiState.value = _uiState.value.copy(searchResults = cached)
       return
   }
   ```
3. Store results after search
4. Clear cache on `clearIndex()` or document changes

### Task: Add UI Tests for Search Flow

1. Create `OllamaRAGScreenTest.kt` in `androidTest/`
2. Use `createComposeRule()` to test composables
3. Mock `RAGViewModel` with test doubles
4. Example test:
   ```kotlin
   @Test
   fun searchButton_isDisabled_whenNoDocuments() {
       composeTestRule.setContent {
           OllamaRAGScreen(viewModel = mockViewModel)
       }
       composeTestRule.onNodeWithTag("searchButton")
           .assertIsNotEnabled()
   }
   ```

### Task: Migrate to Hilt Dependency Injection

Currently dependencies are manually created in ViewModel. To use Hilt:

1. Create `di/AppModule.kt`:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object AppModule {
       @Provides
       @Singleton
       fun provideOllamaClient(): OllamaClient = OllamaClient()

       @Provides
       fun provideDocumentParser(@ApplicationContext context: Context) =
           DocumentParser(context)
   }
   ```
2. Add `@HiltViewModel` to `RAGViewModel` and inject dependencies:
   ```kotlin
   @HiltViewModel
   class RAGViewModel @Inject constructor(
       private val ollamaClient: OllamaClient,
       // ...
   ) : ViewModel()
   ```
3. Add `@AndroidEntryPoint` to `MainActivity`
4. Create `MivDatingApplication.kt` with `@HiltAndroidApp`

---

## Important Constraints

### Performance Considerations

- **In-memory search:** All embeddings loaded for each search. For >10k chunks, consider:
  - SQLite FTS (Full-Text Search) pre-filtering
  - Approximate nearest neighbor libraries (e.g., Voyager, Annoy)
  - Pagination or limit loaded embeddings
- **Embedding latency:** ~100-500ms per chunk. For large docs, show progress.
- **Database writes:** Use transactions for batch operations (already implemented)

### Ollama Requirements

- **Network:** App expects Ollama at `10.0.2.2:11434` (emulator) or `localhost:11434` (physical device with port forwarding)
- **Model availability:** `nomic-embed-text` must be pulled (`ollama pull nomic-embed-text`)
- **Timeouts:** Increase for slow machines or large texts (currently 60s)

### Android-Specific

- **Lifecycle:** ViewModel survives configuration changes; database is reopened if needed
- **Permissions:** None required (assets only)
- **Background work:** Indexing runs on `viewModelScope`; kills on activity destruction

---

## Testing the App

### Manual Testing Checklist

1. **Initial State:**
   - ✅ Ollama status indicator correct (green/red)
   - ✅ Document/chunk counts are 0
   - ✅ Index and search buttons in correct state

2. **Indexing:**
   - ✅ Click "Индексировать книги"
   - ✅ Progress updates appear (parsing, chunking, embedding, saving)
   - ✅ Progress bar fills during embedding
   - ✅ Completion message shows correct counts
   - ✅ Document list displays indexed books

3. **Search:**
   - ✅ Search disabled before indexing
   - ✅ Search enabled after indexing
   - ✅ Enter query and click search
   - ✅ Results display with similarity scores
   - ✅ Results sorted by score (descending)

4. **Clear Index:**
   - ✅ Click trash icon
   - ✅ Counts reset to 0
   - ✅ Search disabled
   - ✅ Previous results cleared

5. **Error Handling:**
   - ❌ Stop Ollama → index → error message appears
   - ❌ Malformed file in assets → graceful error

### Unit Test Suggestions

- `TextChunker`: Test chunk boundaries, overlap, edge cases (empty text, single chunk)
- `VectorDatabase`: Test CRUD operations, cosine similarity accuracy
- `DocumentParser`: Test TXT/HTML parsing, edge cases
- `OllamaClient`: Mock HTTP responses, test error handling

### UI Test Suggestions

- Navigation flow: indexing → search → results
- State persistence across configuration changes
- Error state display

---

## Troubleshooting

### "Ollama недоступен" (Ollama Unavailable)

**Causes:**
- Ollama not running on host
- Incorrect `OLLAMA_HOST` environment variable
- Firewall blocking port 11434
- Wrong base URL in `OllamaClient`

**Solutions:**
1. Run `./start-ollama.sh` on host machine
2. Verify Ollama running: `curl http://localhost:11434/api/tags`
3. For physical device, use `adb reverse tcp:11434 tcp:11434`

### Indexing Hangs on Embedding

**Causes:**
- Ollama model not downloaded
- Network timeout too short
- Ollama server overloaded

**Solutions:**
1. Pull model: `ollama pull nomic-embed-text`
2. Increase timeout in `OllamaClient`:
   ```kotlin
   requestTimeoutMillis = 120_000  // 2 minutes
   ```
3. Check Ollama logs for errors

### Search Returns No Results

**Causes:**
- No documents indexed
- Query embedding failed
- Similarity threshold too high (none set currently)

**Solutions:**
1. Check `chunksCount > 0` before searching
2. Verify Ollama connection
3. Add logging to `searchSimilar()` to debug scores

### App Crashes on Large Documents

**Causes:**
- Out of memory (loading all embeddings)
- Stack overflow in chunking

**Solutions:**
1. Increase app heap size in `AndroidManifest.xml`:
   ```xml
   <application android:largeHeap="true">
   ```
2. Paginate search results
3. Optimize chunking to avoid deep recursion

### Database Version Conflicts

**Causes:**
- Schema changed but version not incremented

**Solutions:**
1. Increment `DATABASE_VERSION` in `VectorDatabase`
2. Or clear app data: Settings → Apps → MivDating → Clear Data

---

## Git Workflow

### Branch Naming

- Feature branches: `claude/claude-md-{session-id}`
- Always push to designated branch (see git configuration)

### Commit Messages

Follow conventional commits:
- `feat: add PDF parsing support`
- `fix: handle empty search results`
- `refactor: extract chunking logic`
- `docs: update CLAUDE.md with new dependencies`

**Style:**
- Imperative mood ("add" not "added")
- Lowercase
- No period at end
- Body optional (explain "why" if needed)

### Pushing Changes

```bash
git add .
git commit -m "feat: implement batch embedding optimization"
git push -u origin claude/claude-md-{session-id}
```

**Important:** Always push to branches starting with `claude/` and ending with session ID.

---

## Resources

### Official Documentation

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Ktor Client](https://ktor.io/docs/client.html)
- [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [SQLite Android](https://developer.android.com/training/data-storage/sqlite)

### Related Concepts

- **RAG (Retrieval Augmented Generation):** Technique to enhance LLM responses with relevant document chunks
- **Vector Embeddings:** Dense numerical representations of text for similarity search
- **Cosine Similarity:** Measure of similarity between two vectors (1 = identical, 0 = orthogonal)
- **Semantic Search:** Search based on meaning rather than keyword matching

### Ollama Models for Embedding

- `nomic-embed-text` (default): 768-dimensional, optimized for RAG
- `all-minilm`: 384-dimensional, faster but less accurate
- `bge-large`: 1024-dimensional, higher quality

---

## Future Enhancements

### High Priority

1. **Implement Hilt DI:** Remove manual dependency creation
2. **Add more document formats:** PDF, DOCX, Markdown
3. **Optimize search:** Use approximate nearest neighbor (ANN) for large indexes
4. **Add query rewriting:** Expand user query for better retrieval

### Medium Priority

1. **Export/import index:** Save/load embeddings to file
2. **Multi-query search:** Combine multiple queries with weights
3. **Highlighting:** Show matched text in search results
4. **Pagination:** Load results incrementally

### Low Priority

1. **Dark mode:** Already theme-aware via MaterialTheme
2. **Settings screen:** Configure chunk size, model, etc.
3. **Document management:** Delete specific documents from index
4. **Analytics:** Track search quality, popular queries

---

## License & Credits

**Project:** MivDating RAG Application
**Author:** kamikaz-e
**License:** Not specified (check repository)

**Key Libraries:**
- Jetpack Compose by Google
- Ktor by JetBrains
- Ollama by Ollama team
- Jsoup by Jonathan Hedley

---

## Last Updated

This document reflects the codebase state as of **2025-11-25**.

For questions or updates, refer to the repository maintainer or update this file accordingly.
