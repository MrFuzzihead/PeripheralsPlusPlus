--[[
Created by Fxz_y
]]
local analyzers = { "beeAnalyzer", "butterflyAnalyzer", "treeAnalyzer" }

local p
for _, name in pairs(analyzers) do
  p = peripheral.find(name)
  if p then
    break
  end
end

if not p then
  print("No analyzer attached.")
  return
end

if not p.isMember() then
  print("The item in the analyzer can't be recognised!")
  return
end

for key, value in pairs(p.analyze()) do
  if type(value) == "table" then
    for subKey, subValue in pairs(value) do
      print(tostring(key) .. "." .. tostring(subKey) .. ": " .. string.lower(tostring(subValue)))
    end
  else
    print(tostring(key) .. ": " .. string.lower(tostring(value)))
  end
end
