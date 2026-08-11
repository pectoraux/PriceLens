from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, BigInteger, DateTime, Float, LargeBinary, JSON
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.sql import func
import uuid
from app.core.database import Base

class Observation(Base):
    __tablename__ = "observation"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    item_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), nullable=False)
    locality_id = Column(BigInteger, ForeignKey("locality.id"), nullable=False)

    price_minor = Column(BigInteger, nullable=False)
    currency_code = Column(String(3), nullable=False)
    quantity = Column(Float, nullable=False)
    unit = Column(String, nullable=False)

    observed_at = Column(DateTime(timezone=True), nullable=False)
    submitted_at = Column(DateTime(timezone=True), server_default=func.now())

    predicted_item_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), nullable=True)
    predicted_confidence = Column(Float)
    label_was_corrected = Column(Boolean, default=False)
    price_was_corrected = Column(Boolean, default=False)
    abstained = Column(Boolean, default=False)

    status = Column(String, default="pending")
    device_class_id = Column(String, nullable=False)
    model_versions = Column(JSON, nullable=False)
