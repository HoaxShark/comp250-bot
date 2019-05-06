# The code for my bot is at [bot/src/bot](https://github.com/HoaxShark/comp250-bot/tree/master/bot/src/bot)

# comp250-bot

This is the base repository for COMP250 assignment 1, task 2 (MicroRTS bot).

## [Tournament leaderboard](http://comp250.falmouth.games) -- [Alternative link](http://10.3.153.12)

Note: the tournament leaderboard is only available on campus or via VPN -- refer to the VPN access guide on the [Games Academy resource page](https://learningspace.falmouth.ac.uk/course/view.php?id=3301)

## Getting started

* Install [Java SE Development Kit 8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and [Eclipse](https://www.eclipse.org/downloads/)
* Fork this repository
* Open Eclipse and create a new workspace
* Open the File menu and select Import
   - On the first screen, select "Git -> Projects from Git"
   - On the next screen, select "Clone URI"
   - On the next screen, enter the clone URL for your fork of the GitHub repository, and enter your GitHub username and password
   - On the next screen, select the Master branch
   - On the next screen, select a location to clone the repository, and **ensure the "Clone submodules" box is checked**
   - Click through the rest of the screens accepting the defaults, and click Finish
* To edit your bot:
   - Find `bot/src/tests/RandomAI.java` in the Package Explorer and double click to edit it
   - You will probably want to rename this class
* To test your bot:
   - Find `bot/src/tests/GameVisualSimulationTest.java` in the Package Explorer
   - Right-click it and do "Run as -> Java application"
   - If you rename your bot class, edit `GameVisualSimulationTest.java` accordingly
* To run your bot on the tournament leaderboard:
   - Push your code to the master branch of your forked repository
   - Go to the leaderboard page (link above)
   - First time: enter your Git repository URL in the "Add bot" box and click the "Add" button
   - Subsequently: find your bot in the list and click "Update now"
   
