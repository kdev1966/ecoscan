// EcoScan — racine. Plugins déclarés ici, appliqués dans :app.
// Mêmes versions qu'AuraNote (validées sur le Redmi Note 8, appareil de référence). Rester sur AGP 8.x.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
