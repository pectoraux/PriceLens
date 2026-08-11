from fastapi import FastAPI
from app.api.v1 import price, catalog, capture, observations, models, analytics

app = FastAPI(title="PriceLens API", version="1.0.0")

@app.get("/")
async def root():
    return {"message": "Welcome to PriceLens API"}

app.include_router(price.router, prefix="/v1/price", tags=["Price"])
app.include_router(catalog.router, prefix="/v1/catalog", tags=["Catalog"])
app.include_router(capture.router, prefix="/v1/capture", tags=["Capture"])
app.include_router(observations.router, prefix="/v1/observations", tags=["Observations"])
app.include_router(models.router, prefix="/v1/models", tags=["Models"])
app.include_router(analytics.router, prefix="/v1/analytics", tags=["Analytics"])
