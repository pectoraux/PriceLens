from pydantic import BaseModel
from typing import Optional, Dict

class PriceEstimateRequest(BaseModel):
    itemSlug: str
    unit: str
    quantity: float
    geohash6: str
    currencyCode: str
    observedAt: Optional[str] = None

class PriceBand(BaseModel):
    p10Minor: int
    p50Minor: int
    p90Minor: int
    currencyCode: str
    unit: str
    nObservations: int
    nContributors: int
    freshnessDays: int
    confidence: str # HIGH|MEDIUM|LOW|INSUFFICIENT
    sourceMix: Dict[str, int]
