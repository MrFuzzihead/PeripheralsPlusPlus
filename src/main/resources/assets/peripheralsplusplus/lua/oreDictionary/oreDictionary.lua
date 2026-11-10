--[[
Created by Fxz_y

Waits for a player to right-click the ore dictionary with an item and prints the ore dictionary entries.
]]
local p = peripheral.find("oreDictionary")
if not p then
  print("No ore dictionary attached.")
  return
end

local _, entries = os.pullEvent("oreDict")
for key, value in pairs(entries) do
  print(tostring(key) .. ": " .. tostring(value))
end
