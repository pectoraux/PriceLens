from fastapi import APIRouter
import uuid
from datetime import datetime, timedelta
from app.schemas.capture import NonceResponse

router = APIRouter()

@router.post("/nonce", response_model=NonceResponse)
async def create_nonce():
    # In production, this would be stored in Redis with a 10-minute TTL
    nonce = str(uuid.uuid4())
    expires_at = datetime.utcnow() + timedelta(minutes=10)
    return NonceResponse(nonce=nonce, expiresAt=expires_at)
