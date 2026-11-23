package org.example.isolation;

/**
 * Presente uniquement cote parent : prouve le repli sur le parent.
 */
public final class ParentOnlyProbe {

    private ParentOnlyProbe() {
    }

    public static String origin() {
        return "parent";
    }
}
