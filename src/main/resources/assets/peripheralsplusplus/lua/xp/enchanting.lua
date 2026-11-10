--[[
Created by Fxz_y

A small control panel for the XP turtle: collect, bottle and enchant.
]]
local p = peripheral.find("xp")
if not p then
  print("No XP turtle attached.")
  return
end

local collecting = true
local levels = 0
local xp = 0

local function draw()
  term.clear()
  term.setCursorPos(1, 1)
  print("Levels: " .. tostring(levels) .. ".")
  print("XP: " .. tostring(xp) .. ".")
  print(collecting and "Collecting XP: On." or "Collecting XP: Off.")
  print("Press X to toggle XP collecting.")
  print("Press B to use bottles o' enchanting from slot 1.")
  print("Press E to enchant the item in slot 1.")
  print("Press R to refresh.")
end

local function enchant()
  term.clear()
  term.setCursorPos(1, 1)
  print("What level do you want your enchant to be?")
  local requested = tonumber(read())
  if not requested then
    print("That is not a number.")
    sleep(1)
    return
  end
  if requested > levels then
    print("You only have " .. tostring(levels) .. " levels.")
    sleep(1)
    return
  end
  p.enchant(requested)
end

while true do
  levels = p.getLevels()
  xp = p.getXP()
  p.setAutoCollect(collecting)
  draw()

  local event, char = os.pullEvent()
  if event == "char" then
    if char == "x" then
      collecting = not collecting
    elseif char == "b" then
      p.add()
    elseif char == "e" then
      enchant()
    end
  end
end
