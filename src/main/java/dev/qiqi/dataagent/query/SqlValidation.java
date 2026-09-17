package dev.qiqi.dataagent.query;

public record SqlValidation(boolean valid, String reason, String safeSql) {
    public static SqlValidation reject(String reason) {
        return new SqlValidation(false, reason, null);
    }

    public static SqlValidation allow(String sql) {
        return new SqlValidation(true, "OK", sql);
    }
}
