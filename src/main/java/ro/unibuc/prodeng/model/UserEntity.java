package ro.unibuc.prodeng.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class UserEntity {
    @Id
    private String id;
    private String username;
    private String name;
    private String email;
    private String password;
    @Builder.Default
    private List<RoleEntity> roles = new ArrayList<>();
}
