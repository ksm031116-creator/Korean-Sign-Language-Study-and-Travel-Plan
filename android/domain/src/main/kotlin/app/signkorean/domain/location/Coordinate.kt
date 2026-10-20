package app.signkorean.domain.location

/** WGS84 좌표. 장소의 실제 위치이며 지역 중심점으로 대신하지 않는다 (FR13). */
data class Coordinate(val lat: Double, val lng: Double) {
    init {
        require(lat in -90.0..90.0) { "위도 범위 밖: $lat" }
        require(lng in -180.0..180.0) { "경도 범위 밖: $lng" }
    }
}
