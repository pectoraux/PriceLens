from fastapi import APIRouter
from typing import List
from datetime import date, timedelta
from pydantic import BaseModel

router = APIRouter()

class PriceHistoryEntry(BaseModel):
    week_start: date
    median_price: int
    n_observations: int

@router.get("/prices", response_model=List[PriceHistoryEntry])
async def get_locality_price_history(geohash6: str, item_slug: str):
    # Mock data for NGOs/Researchers
    today = date.today()
    return [
        PriceHistoryEntry(week_start=today - timedelta(weeks=i), median_price=200 + i*5, n_observations=15+i)
        for i in range(12)
    ]

@router.get("/health")
async def health_check():
    return {"status": "healthy", "version": "1.0.0"}

@router.get("/metrics")
async def get_metrics():
    # Placeholder for Prometheus/Grafana metrics
    return {
        "active_contributors": 150,
        "total_observations": 45000,
        "mean_inference_latency_ms": 420,
        "agreement_rate": 0.88
    }
