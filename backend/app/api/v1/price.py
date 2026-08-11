from fastapi import APIRouter
from app.schemas.price import PriceEstimateRequest, PriceBand

router = APIRouter()

@router.post("/estimate", response_model=PriceBand)
async def estimate_price(request: PriceEstimateRequest):
    # Mock Bayesian model response
    return PriceBand(
        p10Minor=180,
        p50Minor=220,
        p90Minor=280,
        currencyCode=request.currencyCode,
        unit=request.unit,
        nObservations=23,
        nContributors=11,
        freshnessDays=2,
        confidence="HIGH",
        sourceMix={"user_confirmed": 19, "public": 3, "receipt_ocr": 1}
    )
