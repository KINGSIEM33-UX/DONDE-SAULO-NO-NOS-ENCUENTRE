package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaStorageHelperTest {
    @Test
    fun isRemoteMediaUrl_rejectsLocalReferencesAndPresets() {
        assertThat(MediaStorageHelper.isRemoteMediaUrl("/data/user/0/app/files/photo.jpg")).isFalse()
        assertThat(MediaStorageHelper.isRemoteMediaUrl("file:///data/user/0/app/files/photo.jpg")).isFalse()
        assertThat(MediaStorageHelper.isRemoteMediaUrl("content://media/external/images/1")).isFalse()
        assertThat(MediaStorageHelper.isRemoteMediaUrl("story_sunset")).isFalse()
    }

    @Test
    fun isRemoteMediaUrl_acceptsHttpStorageUrls() {
        assertThat(MediaStorageHelper.isRemoteMediaUrl("https://firebasestorage.googleapis.com/v0/b/app/o/photo")).isTrue()
        assertThat(MediaStorageHelper.isRemoteMediaUrl("http://localhost:9199/v0/b/app/o/photo")).isTrue()
    }

    @Test
    fun isLocalMediaReference_distinguishesLocalAndRemoteValues() {
        assertThat(MediaStorageHelper.isLocalMediaReference("/data/user/0/app/files/photo.jpg")).isTrue()
        assertThat(MediaStorageHelper.isLocalMediaReference("file:///data/user/0/app/files/photo.jpg")).isTrue()
        assertThat(MediaStorageHelper.isLocalMediaReference("content://media/external/images/1")).isTrue()
        assertThat(MediaStorageHelper.isLocalMediaReference("https://firebasestorage.googleapis.com/v0/b/app/o/photo")).isFalse()
        assertThat(MediaStorageHelper.isLocalMediaReference("story_sunset")).isFalse()
    }
}
