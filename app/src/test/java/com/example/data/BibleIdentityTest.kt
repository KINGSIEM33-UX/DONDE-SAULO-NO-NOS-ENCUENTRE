package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BibleIdentityTest {
    @Test
    fun knownAliasUsesStableAppId() {
        assertThat(BibleClient.canonicalBibleId("rvr1960")).isEqualTo("RV1960")
    }

    @Test
    fun unknownApiBibleIdIsNotGuessed() {
        assertThat(BibleClient.canonicalBibleId("api-bible-custom-id")).isEqualTo("api-bible-custom-id")
    }
}
