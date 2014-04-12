package org.quuux.eyecandy;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import org.quuux.orm.Database;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.QueryListener;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EyeCandyDatabase extends Database {

    private static final String NAME = "eyecandy.db";
    private static final int VERSION = 13;
    private static final String TAG = Log.buildTag(EyeCandyDatabase.class);

    private static EyeCandyDatabase instance = null;

    // TODO
    // http://headlikeanorange.tumblr.com/
    // http://radar.weather.gov/Conus/Loop/NatLoop.gif
    // romain guy's
    // wikipaintings

    public static final String SUBREDDITS[] = {
            "woahdude",
            "Cinemagraphs",
            "gifs",
            "naturegifs",
            "oldschoolcool",
            "aww",
            "perfectloops",

            // $('blockquote a').map(function() {return this.text.replace('/r/', '')});
            "ShittyEarthPorn", "VideoPorn", "Scholastic", "HistoryPorn", "MapPorn", "BookPorn", "NewsPorn", "QuotesPorn", "FuturePorn", "FossilPorn", "Aesthetic", "DesignPorn", "AlbumArtPorn", "MetalPorn", "MoviePosterPorn", "AdPorn", "GeekPorn", "RoomPorn", "InstrumentPorn", "MacroPorn", "MicroPorn", "ArtPorn", "FractalPorn", "ExposurePorn", "StreetArtPorn", "AVPorn", "powerwashingporn", "uniformporn", "mtgporn", "GamerPorn", "Organic", "AnimalPorn", "BotanicalPorn", "HumanPorn", "AdrenalinePorn", "ClimbingPorn", "CulinaryPorn", "FoodPorn", "DessertPorn", "AgriculturePorn", "TeaPorn", "MegalithPorn", "SportsPorn", "mushroomporn", "Synthetic", "CityPorn", "VillagePorn", "AbandonedPorn", "InfrastructurePorn", "MachinePorn", "MilitaryPorn", "CemeteryPorn", "ArchitecturePorn", "CarPorn", "F1Porn", "GunPorn", "KnifePorn", "BoatPorn", "AerialPorn", "RuralPorn", "RidesPorn", "HousePorn", "thingscutinhalfporn", "cabinporn", "Elemental", "EarthPorn", "WaterPorn", "SkyPorn", "SpacePorn", "FirePorn", "DestructionPorn", "GeologyPorn", "WinterPorn", "AutumnPorn", "weatherporn"
    };

    protected EyeCandyDatabase(final Context context, final String name, final int version) {
        super(context, name, version);
    }

    public static EyeCandyDatabase getInstance(final Context context) {

        if (instance == null) {
            instance = new EyeCandyDatabase(context.getApplicationContext(), NAME, VERSION);
        }

        return instance;
    }

    public static Session getSession(final Context context) {
        return getInstance(context).createSession();
    }

}

