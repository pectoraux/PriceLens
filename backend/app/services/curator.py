import numpy as np
from typing import List, Dict, Set

class ModelCurator:
    """
    Handles robust model updates, prototype centroid calculations,
    and reversible learning.
    """

    def geometric_median(self, points: np.ndarray, eps: float = 1e-5, max_iter: int = 100) -> np.ndarray:
        """
        Calculates the geometric median using Weiszfeld's algorithm.
        Robust against outlier embeddings in a class.
        """
        if points.shape[0] == 0:
            return np.zeros(points.shape[1])

        # Start with arithmetic mean as initial guess
        median = np.mean(points, axis=0)

        for _ in range(max_iter):
            # Calculate distances from current median to all points
            distances = np.linalg.norm(points - median, axis=1)

            # Avoid division by zero
            distances = np.where(distances < eps, eps, distances)

            weights = 1.0 / distances
            new_median = np.sum(points * weights[:, np.newaxis], axis=0) / np.sum(weights)

            if np.linalg.norm(new_median - median) < eps:
                return new_median

            median = new_median

        return median

    def update_prototype(self, embeddings: List[np.ndarray]) -> np.ndarray:
        """
        Updates a prototype centroid using the robust geometric median.
        """
        if not embeddings:
            raise ValueError("No embeddings provided for update")

        points = np.stack(embeddings)
        return self.geometric_median(points)

    def rebuild_prototype_excluding_contributors(
        self,
        all_embeddings: List[Dict], # List of {'embedding': np.ndarray, 'contributor_id': str}
        excluded_ids: Set[str]
    ) -> np.ndarray:
        """
        Influence Reversal: Rebuilds a centroid by completely excluding specific contributors.
        """
        filtered = [e['embedding'] for e in all_embeddings if e['contributor_id'] not in excluded_ids]
        if not filtered:
            return np.zeros(all_embeddings[0]['embedding'].shape)

        return self.geometric_median(np.stack(filtered))

    def evaluate_shadow_mode(self, prod_pred: str, candidate_pred: str, user_correction: str = None) -> Dict:
        """
        Compares candidate model performance against production.
        """
        result = {
            "agreed": prod_pred == candidate_pred,
            "candidate_matches_user": candidate_pred == user_correction if user_correction else None,
            "prod_matches_user": prod_pred == user_correction if user_correction else None
        }
        return result
