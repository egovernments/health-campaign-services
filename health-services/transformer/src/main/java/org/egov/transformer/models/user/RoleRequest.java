package org.egov.transformer.models.user;

import lombok.*;
import digit.models.coremodels.user.Role;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"code", "tenantId"})
public class RoleRequest {

    private String code;
    private String name;
    private String tenantId;

    public RoleRequest(Role domainRole) {
        this.code = domainRole.getCode();
        this.name = domainRole.getName();
        this.tenantId = domainRole.getTenantId();
    }

    public Role toDomain() {
        return Role.builder()
                .code(code)
                .name(name)
                .tenantId(tenantId)
                .build();
    }
}
