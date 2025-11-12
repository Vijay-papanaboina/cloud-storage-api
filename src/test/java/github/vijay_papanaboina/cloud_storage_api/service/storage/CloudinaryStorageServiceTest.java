package github.vijay_papanaboina.cloud_storage_api.service.storage;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CloudinaryStorageServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @InjectMocks
    private CloudinaryStorageService cloudinaryStorageService;

    private String testPublicId;

    @BeforeEach
    void setUp() {
        testPublicId = "test-public-id-123";
        // Clear cache before each test
        clearCache();
    }

    @Test
    void resolveResourceType_CacheHit_ReturnsCachedValue() throws Exception {
        // Given: Cache already contains the resource type
        String cachedResourceType = "video";
        populateCache(testPublicId, cachedResourceType);

        // When: resolveResourceType is called
        String result = invokeResolveResourceType(testPublicId);

        // Then: Should return cached value without calling Admin API
        assertThat(result).isEqualTo(cachedResourceType);
        verify(cloudinary, never()).api();
    }

    @Test
    void resolveResourceType_CacheMiss_ReturnsNullWhenApiNotMocked() throws Exception {
        // Given: Cache is empty (no mock setup for Admin API)
        // When: resolveResourceType is called
        String result = invokeResolveResourceType(testPublicId);

        // Then: Should return null when API is not available (triggers fallback)
        // This verifies the fallback behavior when cache miss occurs
        assertThat(result).isNull();
    }

    @Test
    void resolveResourceType_CacheExpired_DetectsExpiration() throws Exception {
        // Given: Cache contains expired entry
        String oldResourceType = "raw";
        populateCacheWithExpiredEntry(testPublicId, oldResourceType);

        // When: resolveResourceType is called with expired cache
        String result = invokeResolveResourceType(testPublicId);

        // Then: Should detect expiration and return null (will attempt to refetch)
        // Since Admin API is not mocked, it will return null
        assertThat(result).isNull();
    }

    @Test
    void resolveResourceType_CacheBehavior_StoresAndRetrievesCorrectly() throws Exception {
        // Test cache storage and retrieval using reflection
        String testResourceType = "image";
        populateCache(testPublicId, testResourceType);

        // Verify cache contains the entry
        Field cacheField = CloudinaryStorageService.class.getDeclaredField("resourceTypeCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> cache = (ConcurrentHashMap<String, Object>) cacheField
                .get(cloudinaryStorageService);

        assertThat(cache).containsKey(testPublicId);

        // Verify cache retrieval
        String result = invokeResolveResourceType(testPublicId);
        assertThat(result).isEqualTo(testResourceType);
    }

    @Test
    void resolveResourceType_CacheTTL_ExpiresAfterTTL() throws Exception {
        // Given: Cache contains entry that will expire
        String testResourceType = "video";
        populateCacheWithExpiredEntry(testPublicId, testResourceType);

        // When: resolveResourceType is called
        String result = invokeResolveResourceType(testPublicId);

        // Then: Should return null because entry is expired
        // This verifies TTL-based eviction works
        assertThat(result).isNull();
    }

    // Helper methods to access private methods and fields

    private String invokeResolveResourceType(String publicId) throws Exception {
        Method method = CloudinaryStorageService.class.getDeclaredMethod("resolveResourceType", String.class);
        method.setAccessible(true);
        return (String) method.invoke(cloudinaryStorageService, publicId);
    }

    private void populateCache(String publicId, String resourceType) throws Exception {
        Field cacheField = CloudinaryStorageService.class.getDeclaredField("resourceTypeCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> cache = (ConcurrentHashMap<String, Object>) cacheField
                .get(cloudinaryStorageService);

        // Create a cache entry using reflection
        Class<?> cacheEntryClass = Class.forName(
                "github.vijay_papanaboina.cloud_storage_api.service.storage.CloudinaryStorageService$CacheEntry");
        java.lang.reflect.Constructor<?> constructor = cacheEntryClass.getDeclaredConstructor(String.class, long.class);
        constructor.setAccessible(true);
        Object cacheEntry = constructor.newInstance(resourceType, System.currentTimeMillis());

        cache.put(publicId, cacheEntry);
    }

    private void populateCacheWithExpiredEntry(String publicId, String resourceType) throws Exception {
        Field cacheField = CloudinaryStorageService.class.getDeclaredField("resourceTypeCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> cache = (ConcurrentHashMap<String, Object>) cacheField
                .get(cloudinaryStorageService);

        // Create an expired cache entry (timestamp from 10 minutes ago)
        Class<?> cacheEntryClass = Class.forName(
                "github.vijay_papanaboina.cloud_storage_api.service.storage.CloudinaryStorageService$CacheEntry");
        java.lang.reflect.Constructor<?> constructor = cacheEntryClass.getDeclaredConstructor(String.class, long.class);
        constructor.setAccessible(true);
        long expiredTimestamp = System.currentTimeMillis() - (10 * 60 * 1000); // 10 minutes ago
        Object cacheEntry = constructor.newInstance(resourceType, expiredTimestamp);

        cache.put(publicId, cacheEntry);
    }

    private void clearCache() {
        try {
            Field cacheField = CloudinaryStorageService.class.getDeclaredField("resourceTypeCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Object> cache = (ConcurrentHashMap<String, Object>) cacheField
                    .get(cloudinaryStorageService);
            cache.clear();
        } catch (Exception e) {
            // Ignore if cache doesn't exist yet
        }
    }
}
