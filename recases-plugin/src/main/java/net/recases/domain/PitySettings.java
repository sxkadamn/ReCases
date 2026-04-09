package net.recases.domain;

public final class PitySettings {

    private final int hardGuaranteeAfterOpens;
    private final boolean curveEnabled;
    private final int curveStartAfter;
    private final double maxRareWeightMultiplier;
    private final double exponent;

    public PitySettings(int hardGuaranteeAfterOpens, boolean curveEnabled, int curveStartAfter, double maxRareWeightMultiplier, double exponent) {
        this.hardGuaranteeAfterOpens = Math.max(0, hardGuaranteeAfterOpens);
        this.curveEnabled = curveEnabled;
        this.curveStartAfter = Math.max(0, curveStartAfter);
        this.maxRareWeightMultiplier = Math.max(1.0D, maxRareWeightMultiplier);
        this.exponent = Math.max(0.1D, exponent);
    }

    public int getHardGuaranteeAfterOpens() {
        return hardGuaranteeAfterOpens;
    }

    public boolean isCurveEnabled() {
        return curveEnabled;
    }

    public int getCurveStartAfter() {
        return curveStartAfter;
    }

    public double getMaxRareWeightMultiplier() {
        return maxRareWeightMultiplier;
    }

    public double getExponent() {
        return exponent;
    }

    public boolean isHardGuaranteeReached(int pityBeforeOpen) {
        return hardGuaranteeAfterOpens > 0 && pityBeforeOpen + 1 >= hardGuaranteeAfterOpens;
    }

    public double getRareWeightMultiplier(int pityBeforeOpen) {
        if (!curveEnabled || maxRareWeightMultiplier <= 1.0D || hardGuaranteeAfterOpens <= 0) {
            return 1.0D;
        }

        int nextOpen = pityBeforeOpen + 1;
        if (nextOpen <= curveStartAfter) {
            return 1.0D;
        }

        double denominator = Math.max(1.0D, hardGuaranteeAfterOpens - curveStartAfter);
        double progress = Math.min(1.0D, Math.max(0.0D, (nextOpen - curveStartAfter) / denominator));
        return 1.0D + (maxRareWeightMultiplier - 1.0D) * Math.pow(progress, exponent);
    }

    public int getProgressPercent(int pityBeforeOpen) {
        if (hardGuaranteeAfterOpens <= 0) {
            return 0;
        }
        return Math.min(100, (int) Math.round(((pityBeforeOpen + 1.0D) * 100.0D) / hardGuaranteeAfterOpens));
    }
}
