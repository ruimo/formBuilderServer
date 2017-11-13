package models;

public enum UserRole {
    ADMIN, NORMAL;

    private static final UserRole byIndex[] = UserRole.class.getEnumConstants();

    public static UserRole byIndex(int index) {
        return byIndex[index];
    }

    public static UserRole[] all() {
        return byIndex.clone();
    }

    public static int maxOrdinal() {
        return byIndex[byIndex.length - 1].ordinal();
    }
}
