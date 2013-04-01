package com.wadpam.open.analytics.google.trackinginfo;

/**
 * Represent a custom variable.
 * Only needed in the old legacy protocol.
 * This feature has been replaced by Custom Dimension and Custom Metric in the
 * new Measurement protocol (v2).
 *
 * @author mattiaslevin
 */
public class CustomVariable {

    public enum Scope {
        VISITOR(1), SESSION(2), PAGE(3);

        private int value;

        private Scope(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * The index of the variable.
     * Must be consistent across requests
     */
    private int index;

    /** The name of the variable */
    private String name;

    /** Value */
    private String value;

    /** The scope of the variable */
    private Scope scope;


    /** Create a custom variable with default page scope */
    public CustomVariable(int index, String name, String value) {
        this(index, name, value, Scope.PAGE);
    }

    /**
     * Create a custom variable.
     * @param index the index of the custom var. MUST be consistent through requests
     * @param name the name of the custom var. MUST be consistent through requests
     * @param value the value
     * @param scope scope
     */
    public CustomVariable(int index, String name, String value, Scope scope) {
        this.index = index;
        this.name = name;
        this.scope = scope;
        this.value = value;
    }


    // Setters and Getters
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
