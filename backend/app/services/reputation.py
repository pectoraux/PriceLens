import numpy as np
from scipy.stats import beta
from typing import Tuple

class ReputationEngine:
    """
    Implements Beta-Bernoulli reputation scoring with Lower Confidence Bound.
    Reputation is category-specific and slow to earn, fast to lose.
    """

    # Weight of a single disagreement vs agreement
    DISAGREEMENT_PENALTY = 2.5

    def update_scores(self, alpha: float, beta_val: float, agreed: bool, weight: float) -> Tuple[float, float]:
        """
        Updates alpha/beta parameters based on a new observation outcome.
        """
        if agreed:
            new_alpha = alpha + weight
            new_beta = beta_val
        else:
            new_alpha = alpha
            new_beta = beta_val + (weight * self.DISAGREEMENT_PENALTY)

        return new_alpha, new_beta

    def calculate_lcb(self, alpha_val: float, beta_val: float, confidence: float = 0.90) -> float:
        """
        Calculates the P10 (10th percentile) of the Beta distribution.
        This ensures users with few observations are not over-trusted.
        """
        # Prior is Beta(1, 1) - uniform
        a = alpha_val + 1.0
        b = beta_val + 1.0

        # ppf is the percent point function (inverse of cdf)
        # We want the 10th percentile (P10)
        return float(beta.ppf(1.0 - confidence, a, b))
