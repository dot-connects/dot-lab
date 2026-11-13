# R8 for release (2026-11-14). No extra keep rules are needed today; checked:
# - config/feeds are org.json by hand (ConfigCodec, Parsers): no reflective (de)serialisation.
# - enums read back by name (enumValueOf / valueOf in ConfigCodec, Migrations, SleepService, Glow): the default
#   proguard-android-optimize.txt keeps values()/valueOf, and name() is the constructor string R8 never renames.
# - services, receivers, activities, the dream and the tile come from the manifest (kept by AAPT rules).
# - fonts and the dream xml are referenced through R.* only, so resource shrinking keeps them.
# Add a rule here when something starts using Class.forName, getIdentifier or reflective JSON.
