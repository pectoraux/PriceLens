import numpy as np
from typing import List, Tuple, Dict

class RobustAggregator:
    """
    Implements outlier-resistant price aggregation using weighted statistics.
    """

    def weighted_quantile(self, values: np.ndarray, weights: np.ndarray, quantile: float) -> float:
        """
        Calculates the weighted quantile.
        """
        sorter = np.argsort(values)
        values = values[sorter]
        weights = weights[sorter]

        cumulative_weight = np.cumsum(weights)
        cutoff = cumulative_weight[-1] * quantile

        return float(np.interp(cutoff, cumulative_weight, values))

    def calculate_mad_outliers(self, values: np.ndarray, threshold: float = 3.5) -> np.ndarray:
        """
        Detects outliers using Median Absolute Deviation (MAD) on log-prices.
        Returns a boolean mask where True = keep, False = outlier.
        """
        if len(values) < 3:
            return np.ones(len(values), dtype=bool)

        log_values = np.log(values)
        median = np.median(log_values)
        abs_deviation = np.abs(log_values - median)
        mad = np.median(abs_deviation)

        if mad == 0:
            return np.ones(len(values), dtype=bool)

        # Consistency constant for normal distribution is 1.4826
        modified_z_score = 0.6745 * abs_deviation / mad
        return modified_z_score <= threshold

    def aggregate_cell(self, prices: List[int], weights: List[float], contributor_ids: List[str]) -> Dict:
        """
        Aggregates a set of observations into a price band.
        Includes outlier rejection and contributor influence caps.
        """
        p = np.array(prices, dtype=float)
        w = np.array(weights, dtype=float)
        c = np.array(contributor_ids)

        # 1. Filter outliers
        keep_mask = self.calculate_mad_outliers(p)
        p_filtered = p[keep_mask]
        w_filtered = w[keep_mask]
        c_filtered = c[keep_mask]

        if len(p_filtered) == 0:
            return {"confidence": "INSUFFICIENT"}

        # 2. Apply Contributor Influence Cap (max 15% per contributor)
        unique_contributors = np.unique(c_filtered)
        total_weight = np.sum(w_filtered)
        max_allowed_per_user = total_weight * 0.15

        for contributor in unique_contributors:
            mask = (c_filtered == contributor)
            user_weight = np.sum(w_filtered[mask])
            if user_weight > max_allowed_per_user:
                # Down-weight all of this user's observations proportionally
                multiplier = max_allowed_per_user / user_weight
                w_filtered[mask] *= multiplier

        # Re-normalize/re-sum after capping
        final_weight = np.sum(w_filtered)

        # 3. Calculate P10, P50, P90
        p10 = self.weighted_quantile(p_filtered, w_filtered, 0.1)
        p50 = self.weighted_quantile(p_filtered, w_filtered, 0.5)
        p90 = self.weighted_quantile(p_filtered, w_filtered, 0.9)

        return {
            "p10": int(p10),
            "p50": int(p50),
            "p90": int(p90),
            "total_weight": float(final_weight),
            "n_observations": len(p_filtered),
            "n_contributors": len(unique_contributors),
            "confidence": "HIGH" if len(p_filtered) >= 10 else "MEDIUM"
        }
