package app.signkorean.adapter.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.StopId
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripStop
import app.signkorean.domain.trip.TripStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 여행 (설계서 §7.2). 날짜는 ISO 문자열(yyyy-MM-dd)이다. 시간대는 Asia/Seoul 고정이라 날짜만 저장한다. */
@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 여행의 하루. 여행을 지우면 함께 지워진다. 출발지는 없음 / 등록 장소 / Apply 한 좌표 중 하나다. */
@Entity(
    tableName = "trip_day",
    primaryKeys = ["trip_id", "date"],
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["trip_id"], onDelete = ForeignKey.CASCADE)],
)
data class TripDayEntity(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "origin_place") val originPlace: String? = null,
    @ColumnInfo(name = "origin_lat") val originLat: Double? = null,
    @ColumnInfo(name = "origin_lng") val originLng: Double? = null,
    @ColumnInfo(name = "origin_label") val originLabel: String? = null,
    @ColumnInfo(name = "revision") val revision: Int,
    @ColumnInfo(name = "needs_recalculation") val needsRecalculation: Boolean,
)

/**
 * 하루의 방문 장소. 같은 날 같은 장소는 데이터베이스도 막는다(UNIQUE, T-08 의 마지막 방어선).
 * 그날이 지워지면 함께 지워진다. [position] 은 그날 안의 순서다. [id] 는 표 전체에서 유일해야 한다 —
 * 편집기는 IdGenerator(UUID)로 만든다. 고정 ID 를 쓰면 다른 여행과 부딪혀 저장이 실패한다(Phase 16 리뷰 TRP-L6).
 */
@Entity(
    tableName = "trip_stop",
    foreignKeys = [
        ForeignKey(
            entity = TripDayEntity::class,
            parentColumns = ["trip_id", "date"],
            childColumns = ["trip_id", "day_date"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trip_id", "day_date", "place_id"], unique = true)],
)
data class TripStopEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "day_date") val dayDate: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "stay_minutes") val stayMinutes: Int,
    @ColumnInfo(name = "position") val position: Int,
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trip ORDER BY start_date, created_at, id")
    suspend fun trips(): List<TripEntity>

    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun trip(id: String): TripEntity?

    @Query("SELECT * FROM trip_day WHERE trip_id = :id ORDER BY date")
    suspend fun days(id: String): List<TripDayEntity>

    @Query("SELECT * FROM trip_stop WHERE trip_id = :id ORDER BY day_date, position")
    suspend fun stops(id: String): List<TripStopEntity>

    @Insert
    suspend fun insertTrip(trip: TripEntity)

    @Insert
    suspend fun insertDays(days: List<TripDayEntity>)

    @Insert
    suspend fun insertStops(stops: List<TripStopEntity>)

    /** 그날들·장소는 CASCADE 로 함께 지워진다. */
    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun delete(id: String)
}

/** 여행 저장소 (설계서 §6.6). 저장은 지우고 다시 넣기를 한 트랜잭션으로 한다 — 중간 상태가 남지 않는다. */
class RoomTripStore(private val db: AppDatabase) : TripStore {
    private val dao = db.tripDao()

    override suspend fun all(): List<Trip> = db.withTransaction { dao.trips().map { read(it) } }

    override suspend fun get(id: TripId): Trip? = db.withTransaction { dao.trip(id.value)?.let { read(it) } }

    override suspend fun save(trip: Trip) = db.withTransaction {
        dao.delete(trip.id.value)
        dao.insertTrip(TripEntity(trip.id.value, trip.title, trip.start.toString(), trip.end.toString(), trip.createdAt.toEpochMilli()))
        dao.insertDays(trip.days.map { day(trip.id, it) })
        dao.insertStops(trip.days.flatMap { d -> d.stops.mapIndexed { i, s -> TripStopEntity(s.id.value, trip.id.value, d.date.toString(), s.place.value, s.stayMinutes, i) } })
    }

    override suspend fun delete(id: TripId) = dao.delete(id.value)

    private suspend fun read(t: TripEntity): Trip {
        val stops = dao.stops(t.id).groupBy { it.dayDate }
        return Trip(
            id = TripId(t.id),
            title = t.title,
            start = LocalDate.parse(t.startDate),
            end = LocalDate.parse(t.endDate),
            days = dao.days(t.id).map { d ->
                TripDay(
                    date = LocalDate.parse(d.date),
                    startTime = LocalTime.ofSecondOfDay(d.startMinute * 60L),
                    origin = origin(d),
                    stops = stops[d.date].orEmpty().map { TripStop(StopId(it.id), PlaceId(it.placeId), it.stayMinutes) },
                    revision = d.revision,
                    needsRecalculation = d.needsRecalculation,
                )
            },
            createdAt = Instant.ofEpochMilli(t.createdAt),
        )
    }

    private fun day(trip: TripId, d: TripDay): TripDayEntity {
        val base = TripDayEntity(trip.value, d.date.toString(), d.startTime.toSecondOfDay() / 60, revision = d.revision, needsRecalculation = d.needsRecalculation)
        return when (val o = d.origin) {
            null -> base
            is Origin.AtPlace -> base.copy(originPlace = o.place.value)
            is Origin.AtCoordinate -> base.copy(originLat = o.coordinate.lat, originLng = o.coordinate.lng, originLabel = o.label)
        }
    }

    private fun origin(d: TripDayEntity): Origin? = when {
        d.originPlace != null -> Origin.AtPlace(PlaceId(d.originPlace))
        d.originLat != null && d.originLng != null -> Origin.AtCoordinate(Coordinate(d.originLat, d.originLng), d.originLabel)
        else -> null
    }
}
