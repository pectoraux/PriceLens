from sqlalchemy import Column, Integer, String, BigInteger, DateTime, CHAR
from sqlalchemy.sql import func
from geoalchemy2 import Geography
from app.core.database import Base

class Locality(Base):
    __tablename__ = "locality"

    id = Column(BigInteger, primary_key=True, index=True)
    geohash6 = Column(CHAR(6), unique=True, nullable=False)
    centroid = Column(Geography(geometry_type='POINT', srid=4326), nullable=False)
    admin_name = Column(String)
    country_code = Column(CHAR(2), nullable=False)
    currency_code = Column(CHAR(3), nullable=False)
    unit_system = Column(String, default="metric")
    market_type = Column(String)
    maturity = Column(String, default="seeding")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
