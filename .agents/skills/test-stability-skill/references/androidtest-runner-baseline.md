# AndroidTest Runner Baseline (feature 模块)

声明 runner 必带依赖：

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

contract guard（PR 3 起）：`FeatureAndroidTestRunnerBaselineContractTest` 静态扫 `feature/*/build.gradle.kts`。
