# Phase 04 - I10-C1 (overlay: copy these files into the MNB repo, same paths)

Modified: app/src/main/java/com/mnb/browser/MainActivity.kt
          app/src/main/java/com/mnb/browser/ui/ReaderScreen.kt
          app/src/main/java/com/mnb/browser/reader/translation/ReaderChapterIdentity.kt
New (main): reader/translation/{NovelBinding, ChapterTitleParser, BoundChapterIdentityResolver,
            TranslationProviderGuard, ReaderTranslationActions, ReaderInstantTranslationController,
            StoredTranslationReaderGateway, InstantTranslationWiring}.kt
New (test): app/src/test/.../reader/translation/*.kt
Not touched: DB schema, InstantTranslationPipeline, TranslationService, queue/WorkManager code, BrowserScreen.
NOT compiled or run here (no Kotlin/Gradle in the sandbox): run CI.
