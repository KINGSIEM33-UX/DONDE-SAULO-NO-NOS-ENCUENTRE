package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        BibleSection::class,
        VerseNote::class,
        FavoriteVerse::class,
        RecentChapter::class,
        StudyEvent::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleSectionDao(): BibleSectionDao
    abstract fun verseNoteDao(): VerseNoteDao
    abstract fun favoriteVerseDao(): FavoriteVerseDao
    abstract fun recentChapterDao(): RecentChapterDao
    abstract fun studyEventDao(): StudyEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Adds translation identity without guessing the source of legacy rows. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `verse_notes` ADD COLUMN `bibleId` TEXT")
                db.execSQL("ALTER TABLE `favorite_verses` ADD COLUMN `bibleId` TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recent_chapters_new` (
                        `chapterId` TEXT NOT NULL,
                        `bibleId` TEXT NOT NULL,
                        `reference` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`chapterId`, `bibleId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `recent_chapters_new` (`chapterId`, `bibleId`, `reference`, `timestamp`)
                    SELECT `chapterId`, `bibleId`, `reference`, `timestamp`
                    FROM `recent_chapters`
                """.trimIndent())
                db.execSQL("DROP TABLE `recent_chapters`")
                db.execSQL("ALTER TABLE `recent_chapters_new` RENAME TO `recent_chapters`")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `dateIso` TEXT NOT NULL,
                        `time` TEXT NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope = appScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "donde_saulo_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(DatabaseCallback(appScope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.bibleSectionDao())
                }
            }
        }

        suspend fun populateDatabase(dao: BibleSectionDao) {
            val sections = listOf(
                BibleSection(
                    id = "tab_1",
                    title = "El Altar de las Ofrendas y Lavamanos",
                    description = "Instrucciones de construcción del altar de sacrificios y su base purificadora.",
                    category = "Tabernáculo",
                    passageReference = "Éxodo 27:1-8, 30:17-21",
                    content = "Construirás también el altar de madera de acacia de cinco codos de longitud, y de cinco codos de anchura; será cuadrado el altar, y su altura de tres codos. Y le harás cuernos en sus cuatro esquinas... Harás también su lavamanos de bronce, con su base de bronce, para lavar; y lo colocarás entre el tabernáculo de reunión y el altar, y pondrás agua en él.",
                    details = "El «Altar de las ofrendas quemadas y todos sus utensilios, y el lavamanos con su base» representan la expiación y la limpieza indispensables para entrar en la presencia soberana. El altar de madera de acacia recubierto de bronce soporta el calor del fuego divino, mientras la fuente de bronce refleja y limpia."
                ),
                BibleSection(
                    id = "tab_2",
                    title = "La Rejilla de Bronce y las Varas",
                    description = "La rejilla de rejado bronceada del altar y varas de transporte en madera de acacia.",
                    category = "Tabernáculo",
                    passageReference = "Éxodo 35:16",
                    content = "El altar del holocausto con su enrejado de bronce, sus varas y todos sus utensilios; la fuente y su base... Harás para el altar un enrejado de bronce en forma de rejilla, y en las cuatro esquinas de la rejilla pondrás cuatro anillos de bronce. Y la pondrás debajo del cerco del altar abajo, para que llegue la rejilla hasta la mitad del altar.",
                    details = "Construcción detallada de el «Altar de las ofrendas quemadas, la rejilla de bronce del altar, las varas para transportarlo y sus utensilios...» Esto permitía el transporte ágil del altar por el desierto, simbolizando la fe itinerante guiada por el fuego del Espíritu."
                ),
                BibleSection(
                    id = "tab_3",
                    title = "Consagración de la Fuente Divina",
                    description = "El altar del holocausto posicionado y la fuente consagrada para ungir sagrarios.",
                    category = "Tabernáculo",
                    passageReference = "Éxodo 40:29-31",
                    content = "Y colocó el altar del holocausto junto a la puerta del tabernáculo, del tabernáculo de reunión, y ofreció sobre él el holocausto y la ofrenda, como Jehová había mandado a Moisés. Y puso la fuente entre el tabernáculo de reunión y el altar, y puso en ella agua para lavarse.",
                    details = "Establecimiento definitivo de el «Altar del holocausto y todos sus utensilios, la fuente y su base...» Moisés unge y consagra estos elementos con aceite sagrado. Todo sacerdote (o Saulo buscando sanación) debía lavarse manos y pies antes de cruzar los umbrales sagrados, recordándonos que la santificación antecede a toda revelación."
                ),
                BibleSection(
                    id = "sac_1",
                    title = "Genealogías y Turnos de Hupa y Jesebeab",
                    description = "Constitución de los turnos de servicio sacerdotal por suertes.",
                    category = "Sacerdotes",
                    passageReference = "1 Crónicas 24:13-19",
                    content = "Los repartieron por suertes los unos con los otros, porque había príncipes del santuario y príncipes de la casa de Dios... la decimatercera a Hupa, la decimacuarta a Jesebeab, la decimoquinta a Bilga, la decimosexta a Imer, la decimoseptima a Hezir...",
                    details = "Bases registradas: «Sus hijos y sus hermanos, doce; la decimatercera a Hupa, la decimacuarta a Jesebeab...» Este ordenamiento de 24 turnos aseguraba la adoración continua y liturgias ordenadas en el templo. Los turnos como el de «La decimoquinta, a Bilga; la decimosexta, a Imer; la decimoseptera a Hupa...» muestran la soberana distribución divina."
                ),
                BibleSection(
                    id = "sac_2",
                    title = "La Suerte de Subael y la Adoración Continua",
                    description = "Servicio con doce sacerdotes para levantar ministerios musicales y litúrgicos.",
                    category = "Sacerdotes",
                    passageReference = "1 Crónicas 25:20-31",
                    content = "Para el servicio de los cantores, jefes de familias sacerdotal de Leví... la decimatercera para Subael, con sus hijos y sus hermanos, doce. La decimonovena para Maloti, sus hijos y hermanos, doce...",
                    details = "Registros detallados de «La decimatercera para Subael, con sus hijos y sus hermanos, doce. La decimonovena, a Maloti y a doce de sus hijos y parientes.» Ningún sacerdote servía solo, sino siempre acompañados («doce hermanos y parientes»), señalando el valor del sacerdocio en comunidad."
                ),
                BibleSection(
                    id = "dev_1",
                    title = "El Pastor Celestial y las Tranquilas Aguas",
                    description = "Salmo de profunda consolación y pastoreo compasivo.",
                    category = "Devocional",
                    passageReference = "Salmo 23",
                    content = "Jehová es mi pastor; nada me faltará. En lugares de de delicados pastos me hará descansar; junto a aguas de reposo me pastoreará. Confortará mi alma; me guiará por sendas de justicia por amor de su nombre.",
                    contentNTV = "El Señor es mi pastor; tengo todo lo que necesito. En verdes prados me deja descansar; me conduce junto a arroyos tranquilos. Él renueva mis fuerzas. Me guía por sendas correctas, y así da honra a su nombre.",
                    contentNVI = "El Señor es mi pastor, nada me falta; en verdes pastos me hace descansar. Junto a tranquilas aguas me conduce; me infunde nuevas fuerzas.",
                    contentTLA = "Tú, Dios mío, eres mi pastor; contigo nada me falta. Me haces descansar en verdes pastos, y para calmar mi sed me llevas a tranquilas aguas.",
                    details = "El pasaje glorioso «Verdes pastos me hace descansar. Junto a tranquilas aguas me conduce» representa la paz que supera el desierto interior de Saulo camino a Damasco, consolando los dolores existenciales."
                ),
                BibleSection(
                    id = "dev_2",
                    title = "Tiempos del Alma y las Piedras",
                    description = "Reflexión del Predicador sobre los ciclos y propósitos celestiales.",
                    category = "Devocional",
                    passageReference = "Eclesiastés 3:1-8",
                    content = "Todo tiene su tiempo, y todo lo que se quiere debajo del cielo tiene su hora... Tiempo de nacer, y tiempo de morir; tiempo de plantar, y tiempo de arrancar lo plantado... Tiempo de esparcir piedras, y tiempo de juntar piedras; tiempo de abrazar, y tiempo de abstenerse de abrazar.",
                    contentNTV = "Hay una temporada para todo, un tiempo para cada actividad bajo el cielo... un tiempo para nacer y un tiempo para morir; un tiempo para sembrar y un tiempo para cosechar... un tiempo para esparcir piedras y un tiempo para juntar piedras...",
                    details = "El pasaje «Un tiempo para esparcir piedras y un tiempo para juntar piedras» enseña que cada etapa espiritual (derribo o edificación) es necesaria. Se aplica a Saulo de Tarso, quien primero arrojó piedras para perseguir de celo, para luego juntarlas en la edificación de la Iglesia universal de Jesucristo."
                ),
                BibleSection(
                    id = "dev_3",
                    title = "La Transformación de Saulo a Paul",
                    description = "Del celo extremista a la renovación de la nueva naturaleza en Colosas.",
                    category = "Devocional",
                    passageReference = "Filipenses 3:5-7, Colosenses 3:10",
                    content = "Circuncidado al octavo día, del linaje de Israel, de la tribu de Benjamín, hebreo de hebreos; en cuanto a la ley, fariseo; en cuanto al celo, perseguidor de la iglesia; en cuanto a la justicia que se basa en la ley, intachable... pero cuantas cosas eran ganancias, las he estimado como pérdidas por amor de Cristo.",
                    details = "Fusión bíblica: «En cuanto al celo, perseguidor de la iglesia; en cuanto a la justicia que la Ley exige, intachable... Se han puesto el de la nueva naturaleza, que se va renovando...» Saulo es encontrado precisamente donde el dolor y el orgullo son derribados. Paul experimenta el despojarse del viejo perseguidor y vestirse con el amor redentor."
                ),
                BibleSection(
                    id = "dev_4",
                    title = "La Solemne Advertencia del Fuego",
                    description = "Advertencia de Marcos sobre los juicios irrevocables del desvío espiritual.",
                    category = "Devocional",
                    passageReference = "Marcos 9:43-48",
                    content = "Y si tu mano te fuere ocasión de caer, córtala; mejor te es entrar en la vida manco, que teniendo dos manos ir al infierno, al fuego que no puede ser apagado... donde el gusano de ellos no muere, y el fuego nunca se apaga.",
                    details = "La severa observación «Donde el gusano de ellos no muere, y el fuego nunca se apaga» nos confronta. Ningún estudio superficial bíblico de Saulo evade la realidad del juicio. Nos recuerda la importancia de ser purificados por el enrejado de bronce sacrificial antes de que la iniquidad nos consuma eternamente."
                )
            )
            dao.insertAll(sections)
        }
    }
}
