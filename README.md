Database server with responsice web-frontend for our family recipes.

## Installation

- get leiningen from [leiningen.org]
- clone or download source and cd to the ```recipes``` directory
- compile JavaScript ```lein cljsbuild once```
- compile server ```lein uberjar```

## Usage

Run target/recipes-x.x.x-standalone.jar
You can visit the frontend on ```localhost:8079```

As the server is intended for our personal use only it can only import
recipes from raw text files.

## Recipes

Text files to be stored in the server must fulfill the following scheme:

    1st line: Title
      - empty line -
    Ingredients, one per line
      - empty line -
    Cooking instructions. Line breaks will be translated to visible breaks.
      - empty line if search tags -
    Optional search tags, separated by commas or line break

For example:

    German Pancakes

    3 cups of flour
    1 egg
    Some milk

    Add milk until it feels right, mix well.
    For each pancake, pour one ladle into a hot pan and bake for a minute or so.
    Watch for audience when flipping, then bake for another minute.
    Throw away the first one, it always gets bad.

    sweet, dough, breakfast, lunch, dessert

