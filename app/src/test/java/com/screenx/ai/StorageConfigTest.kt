package com.screenx.ai

import com.screenx.ai.storage.StorageConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageConfigTest {
    @Test fun keepsNormalNames() = assertEquals("My Videos-2", StorageConfig.sanitize("My Videos-2"))
    @Test fun removesPathCharacters() = assertEquals("abc", StorageConfig.sanitize("a/b\\c:"))
    @Test fun blankFallsBackToDefault() = assertEquals(StorageConfig.DEFAULT_FOLDER, StorageConfig.sanitize("   "))
    @Test fun onlyBadCharactersFallBack() = assertEquals(StorageConfig.DEFAULT_FOLDER, StorageConfig.sanitize("///***"))
    @Test fun longNamesAreCut() = assertTrue(StorageConfig.sanitize("x".repeat(100)).length <= 30)
}
