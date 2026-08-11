import sys
import os
from datetime import datetime

# Add the parent directory to sys.path to import app modules
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from sqlalchemy.orm import Session
from app.core.database import SessionLocal, engine, Base
from app.models.taxonomy import TaxonomyItem, TaxonomyItemName
from app.models.locality import Locality

def seed():
    db = SessionLocal()
    try:
        # 1. Seed Taxonomy
        items = [
            {"slug": "tomato.roma", "name": "Roma Tomato", "category": "produce", "unit": "kg"},
            {"slug": "tomato.cherry", "name": "Cherry Tomato", "category": "produce", "unit": "kg"},
            {"slug": "onion.red", "name": "Red Onion", "category": "produce", "unit": "kg"},
            {"slug": "banana.cavendish", "name": "Banana", "category": "produce", "unit": "bunch"},
            {"slug": "milk.whole", "name": "Whole Milk", "category": "dairy", "unit": "L"},
        ]

        for item_data in items:
            item = TaxonomyItem(
                slug=item_data["slug"],
                canonical_name=item_data["name"],
                category=item_data["category"],
                default_unit=item_data["unit"]
            )
            db.add(item)
            db.flush() # Get ID

            # Add primary name
            name = TaxonomyItemName(
                item_id=item.id,
                locale="en-KE",
                name=item_data["name"],
                is_primary=True
            )
            db.add(name)

        # 2. Seed Locality (Nairobi)
        nairobi = Locality(
            geohash6="6g0p00",
            centroid="POINT(36.8219 -1.2921)", # Nairobi
            admin_name="Nairobi",
            country_code="KE",
            currency_code="KES",
            unit_system="metric",
            maturity="mature"
        )
        db.add(nairobi)

        db.commit()
        print("Seeding completed successfully.")

    except Exception as e:
        db.rollback()
        print(f"Seeding failed: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    # Create tables if they don't exist
    Base.metadata.create_all(bind=engine)
    seed()
