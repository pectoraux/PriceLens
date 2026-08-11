import base64
from typing import List, Dict

class IntegrityVerifier:
    """
    Verifies hardware attestation chains and Play Integrity tokens.
    """

    def verify_hardware_attestation(self, chain_base64: List[str]) -> str:
        """
        Verifies the X.509 certificate chain from Android Key Attestation.
        Returns: Tier (STRONG, STANDARD, BASIC, UNVERIFIED)
        """
        if not chain_base64:
            return "UNVERIFIED"

        # In production:
        # 1. Parse certificates using cryptography library.
        # 2. Verify root is Google's root certificate.
        # 3. Check for StrongBox/TEE flags in the extension data.

        # Placeholder: Assume STRONG if a chain exists for now
        return "STRONG"

    def verify_play_integrity(self, token: str) -> Dict:
        """
        Verifies the Play Integrity token with Google's servers.
        """
        # In production: Use Google API Client to verify token.
        return {
            "deviceIntegrity": "MEETS_STRONG_INTEGRITY",
            "appIntegrity": "PLAY_RECOGNIZED"
        }

class TrustScorer:
    """
    Calculates the trust_weight for an observation.
    """

    def calculate_weight(self, attestation_tier: str, provenance_tier: str, geo_confidence: float) -> float:
        weights = {
            "STRONG": 1.0,
            "STANDARD": 0.85,
            "BASIC": 0.4,
            "UNVERIFIED": 0.05
        }

        prov_weights = {
            "LIVE_CAPTURE": 1.0,
            "RECEIPT_OCR": 1.15,
            "IMPORTED": 0.1,
            "NO_IMAGE": 0.25
        }

        base_weight = weights.get(attestation_tier, 0.05)
        prov_multiplier = prov_weights.get(provenance_tier, 0.25)

        return base_weight * prov_multiplier * geo_confidence
