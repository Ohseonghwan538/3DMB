const altitudeBands = [
  { minimum: 400_000, cellSize: 1 },
  { minimum: 100_000, cellSize: 0.5 },
  { minimum: 30_000, cellSize: 0.2 },
  { minimum: 10_000, cellSize: 0.05 }
];

export function cellSizeForAltitude(altitude) {
  return altitudeBands.find((band) => altitude >= band.minimum)?.cellSize ?? 0;
}

export function clusterFestivals(festivals, cellSize) {
  if (cellSize <= 0) return festivals.map((festival) => ({ kind: "festival", key: `f:${festival.id}`, festival }));
  const cells = new Map();
  for (const festival of festivals) {
    const row = Math.floor(festival.lat / cellSize);
    const column = Math.floor(festival.lng / cellSize);
    const key = `${row}:${column}`;
    const cell = cells.get(key) || { key, row, column, members: [] };
    cell.members.push(festival);
    cells.set(key, cell);
  }
  return [...cells.values()].map((cell) => {
    if (cell.members.length === 1) {
      const festival = cell.members[0];
      return { kind: "festival", key: `f:${festival.id}`, festival };
    }
    const latitude = cell.members.reduce((sum, item) => sum + item.lat, 0) / cell.members.length;
    const longitude = cell.members.reduce((sum, item) => sum + item.lng, 0) / cell.members.length;
    return {
      kind: "cluster",
      key: `c:${cell.key}:${cell.members.length}`,
      count: cell.members.length,
      lat: latitude,
      lng: longitude,
      members: cell.members
    };
  });
}
