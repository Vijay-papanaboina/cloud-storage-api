package github.vijay_papanaboina.cloud_storage_api.repository;

import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by username (for login - POST /api/auth/login).
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email (for registration check - POST /api/auth/register).
     */
    Optional<User> findByEmail(String email);

    /**
     * Find all active users.
     */
    List<User> findByActiveTrue();
}
