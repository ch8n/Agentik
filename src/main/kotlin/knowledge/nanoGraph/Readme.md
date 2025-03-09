Project Structure

```text
nano-graphrag-kotlin/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── client/
│   │   │   │   └── OllamaClient.kt
│   │   │   ├── storage/
│   │   │   │   ├── KuzuDBStorage.kt
│   │   │   │   └── SQLiteVectorStorage.kt
│   │   │   ├── splitter/
│   │   │   │   └── SeparatorSplitter.kt
│   │   │   ├── utils/
│   │   │   │   └── Utils.kt
│   │   │   ├── operations/
│   │   │   │   └── Operations.kt
│   │   │   └── GraphRAG.kt
│   │   └── resources/
│   └── test/
└── settings.gradle.kts
```