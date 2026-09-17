package dev.qiqi.dataagent.identity;

public record UserIdentity(long id, String username, String displayName, String dataScope, Long departmentId) {
    public boolean canQuery() {
        return "ALL".equals(dataScope) || ("DEPARTMENT".equals(dataScope) && departmentId != null);
    }
}
