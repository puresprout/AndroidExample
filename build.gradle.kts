// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // 공통 버전만 등록하고 지금은 적용하지 않음
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false

}