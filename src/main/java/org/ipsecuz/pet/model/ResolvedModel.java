package org.ipsecuz.pet.model;

/**
 * Encapsulates the resolved model rendering decision.
 * Combines provider selection and model ID resolution into a single immutable decision.
 */
public class ResolvedModel {

    private final ModelType provider;
    private final String modelId;
    private final boolean fallback;
    private final boolean legacy;
    private final boolean baby;

    public ResolvedModel(ModelType provider, String modelId, boolean fallback, boolean legacy, boolean baby) {
        this.provider = provider != null ? provider : ModelType.NONE;
        this.modelId = modelId;
        this.fallback = fallback;
        this.legacy = legacy;
        this.baby = baby;
    }

    public static ResolvedModel none(boolean isBaby) {
        return new ResolvedModel(ModelType.NONE, null, false, false, isBaby);
    }

    public ModelType getProvider() {
        return provider;
    }

    public String getModelId() {
        return modelId;
    }

    public boolean isFallback() {
        return fallback;
    }

    public boolean isLegacy() {
        return legacy;
    }

    public boolean isBaby() {
        return baby;
    }

    public boolean isValid() {
        return provider != ModelType.NONE && modelId != null && !modelId.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "ResolvedModel{" +
                "provider=" + provider +
                ", modelId='" + modelId + '\'' +
                ", fallback=" + fallback +
                ", legacy=" + legacy +
                ", baby=" + baby +
                '}';
    }
}
