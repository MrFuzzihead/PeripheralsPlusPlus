--[[
Created by Fxz_y

Usage: note <instrument,note> [instrument,note...]
Example: note 1,2 2,12 1,5 - the first number is the instrument (0-4) and the second the note (0-24).
]]
local p = peripheral.find("noteBlock")
if not p then
  print("No note block attached.")
  return
end

local tArgs = { ... }
if #tArgs == 0 then
  print("Usage: note <instrument,note> [instrument,note...]")
  print("Example: note 1,2 2,12 1,5 (instrument 0-4, note 0-24)")
  return
end

for _, pair in pairs(tArgs) do
  local instrument = tonumber(string.sub(pair, 1, 1))
  local note = tonumber(string.sub(pair, 3))
  if instrument and note and instrument >= 0 and instrument <= 4 and note >= 0 and note <= 24 then
    p.playNote(instrument, note)
    sleep(0.5)
  else
    print("Min: 0,0 , Max: 4,24")
  end
end
