from typing import List, Dict
import numpy as np

class ConsensusEngine:
    """
    Decides when a set of observations reaches 'CONFIRMED' status.
    """

    def evaluate_consensus(self, observations: List[Dict]) -> str:
        """
        Evaluates a group of observations for the same (locality, item, unit).
        Returns: CONFIRMED, DISPUTED, or PENDING
        """
        if len(observations) < 3:
            return "PENDING"

        contributors = set(obs['contributor_id'] for obs in observations)
        if len(contributors) < 3:
            return "PENDING"

        total_weight = sum(obs['trust_weight'] for obs in observations)
        if total_weight < 2.0:
            return "PENDING"

        # Check for high-tier attestation
        has_strong_device = any(obs['attestation_tier'] in ["STRONG", "STANDARD"] for obs in observations)
        if not has_strong_device:
            return "PENDING"

        # Check for price agreement (within 25% on log scale)
        prices = [obs['price_minor'] for obs in observations]
        log_prices = np.log(prices)
        median_log = np.median(log_prices)

        # Are most observations within the tolerance?
        agreement_count = sum(1 for lp in log_prices if abs(lp - median_log) <= 0.25)

        if agreement_count >= 3:
            return "CONFIRMED"
        elif len(observations) >= 5:
            return "DISPUTED"
        else:
            return "PENDING"
