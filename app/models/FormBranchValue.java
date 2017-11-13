package models;

public enum FormBranchValue {
    STAGING, PROD;

    private static final FormBranchValue byIndex[] = FormBranchValue.class.getEnumConstants();

    public static FormBranchValue byIndex(int index) {
        return byIndex[index];
    }

    public static FormBranchValue[] all() {
        return byIndex.clone();
    }

    public static int maxOrdinal() {
        return byIndex[byIndex.length - 1].ordinal();
    }
}
