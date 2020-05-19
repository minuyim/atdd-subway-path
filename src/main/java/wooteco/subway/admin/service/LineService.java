package wooteco.subway.admin.service;

import static java.util.stream.Collectors.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import wooteco.subway.admin.domain.line.Line;
import wooteco.subway.admin.domain.line.LineStation;
import wooteco.subway.admin.domain.line.LineStations;
import wooteco.subway.admin.domain.line.path.EdgeWeightType;
import wooteco.subway.admin.domain.line.path.Path;
import wooteco.subway.admin.domain.line.path.SubwayMap;
import wooteco.subway.admin.domain.line.path.vo.PathInfo;
import wooteco.subway.admin.domain.station.Station;
import wooteco.subway.admin.dto.LineDetailResponse;
import wooteco.subway.admin.dto.LineRequest;
import wooteco.subway.admin.dto.LineStationCreateRequest;
import wooteco.subway.admin.dto.PathRequest;
import wooteco.subway.admin.dto.PathResponse;
import wooteco.subway.admin.dto.PathResponses;
import wooteco.subway.admin.dto.StationResponse;
import wooteco.subway.admin.dto.WholeSubwayResponse;
import wooteco.subway.admin.repository.LineRepository;
import wooteco.subway.admin.repository.StationRepository;

@Service
public class LineService {
    private LineRepository lineRepository;
    private StationRepository stationRepository;

    public LineService(LineRepository lineRepository, StationRepository stationRepository) {
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
    }

    public Line save(Line line) {
        return lineRepository.save(line);
    }

    public List<Line> showLines() {
        return lineRepository.findAll();
    }

    public void updateLine(Long id, LineRequest request) {
        Line persistLine = lineRepository.findById(id).orElseThrow(RuntimeException::new);
        persistLine.update(request.toLine());
        lineRepository.save(persistLine);
    }

    public void deleteLineById(Long id) {
        lineRepository.deleteById(id);
    }

    public void addLineStation(Long id, LineStationCreateRequest request) {
        Line line = lineRepository.findById(id).orElseThrow(RuntimeException::new);
        LineStation lineStation = new LineStation(request.getPreStationId(), request.getStationId(),
            request.getDistance(), request.getDuration());
        line.addLineStation(lineStation);

        lineRepository.save(line);
    }

    public void removeLineStation(Long lineId, Long stationId) {
        Line line = lineRepository.findById(lineId).orElseThrow(RuntimeException::new);
        line.removeLineStationById(stationId);
        lineRepository.save(line);
    }

    public LineDetailResponse findLineWithStationsById(Long id) {
        Line line = lineRepository.findById(id).orElseThrow(RuntimeException::new);
        List<Station> stations = stationRepository.findAllById(line.getLineStationsId());
        return LineDetailResponse.of(line, stations);
    }

    public Long findIdByName(String name) {
        return stationRepository.findByName(name)
            .map(Station::getId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Station 입니다."));
    }

    public PathResponses findPathsBy(PathRequest pathRequest) {
        PathInfo pathInfo = pathRequest.toPathInfo();
        Long departureId = findIdByName(pathInfo.getDepartureStationName());
        Long arrivalId = findIdByName(pathInfo.getArrivalStationName());
        return findPaths(departureId, arrivalId);
    }

    private PathResponses findPaths(Long departureId, Long arrivalId) {
        Map<EdgeWeightType, PathResponse> responses = new HashMap<>();

        LineStations lineStations = new LineStations(lineRepository.findAllLineStations());

        for (EdgeWeightType edgeWeightType : EdgeWeightType.values()) {
            SubwayMap subwayMap = lineStations.toGraph(edgeWeightType);
            responses.put(edgeWeightType, toPathResponse(subwayMap.findShortestPath(departureId, arrivalId)));
        }

        return new PathResponses(responses);
    }

    private PathResponse toPathResponse(Path path) {
        List<Long> shortestPath = path.getPath();
        List<StationResponse> responses = toStationResponses(shortestPath);
        return new PathResponse(responses, path.calculateTotalDuration(),
            path.calculateTotalDistance());
    }

    private List<StationResponse> toStationResponses(List<Long> shortestPath) {
        Map<Long, Station> stationMap = getStationMap();
        return shortestPath.stream()
            .map(stationMap::get)
            .map(StationResponse::of)
            .collect(toList());
    }

    private Map<Long, Station> getStationMap() {
        return stationRepository.findAll()
            .stream()
            .collect(toMap(Station::getId, Function.identity()));
    }

    public WholeSubwayResponse wholeLines() {
        List<Line> lines = lineRepository.findAll();
        return lines.stream()
            .map(line -> LineDetailResponse.of(line, stationRepository.findAllById(line.getLineStationsId())))
            .collect(collectingAndThen(toList(), WholeSubwayResponse::new));
    }
}
