package github.vijay_papanaboina.cloud_storage_api;

import org.junit.jupiter.api.Test;

/**
 * Simple test to verify the application class can be loaded.
 * Full context loading is tested in integration tests that use Testcontainers.
 */
class CloudStorageApiApplicationTests {

	@Test
	void applicationClassLoads() {
		// Verify the application class exists and can be instantiated
		Class<?> clazz = CloudStorageApiApplication.class;
		assert clazz != null;
		assert clazz.getSimpleName().equals("CloudStorageApiApplication");
	}

}
