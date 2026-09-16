Feature: Reelcipe API

  Scenario: User can create, update and delete a recipe
    Given I am authenticated as "alice"
    When I create a recipe with:
      | title          | language | amount | ingredient | unit | step           |
      | Cucumber pasta | EN       | 200    | Pasta      | g    | Boil the pasta |
      | Cucumber pasta | EN       | 500    | Water      | ml   | Add water      |
      | Cucumber pasta | EN       | 1      | Salt       | tsp  | Add salt       |
    Then the recipe is created with version 1
    And the recipe response contains ingredient "Pasta"
    When I update the recipe title to "Cucumber pasta updated"
    Then the recipe is updated with version 2
    When I request the recipe
    Then the recipe title is "Cucumber pasta updated"
    When I delete the recipe
    Then the recipe is deleted
    And requesting the deleted recipe returns 404

  Scenario: Adding a recipe to shopping list is idempotent
    Given I am authenticated as "alice"
    When I create a recipe with:
      | title           | language | ingredient | amount | unit | step            |
      | Shopping recipe | EN       | Tomatoes   | 3      | pcs  | Slice tomatoes  |
      | Shopping recipe | EN       | Olive oil  | 2      | tbsp | Add olive oil   |
      | Shopping recipe | EN       | Salt       | 1      | tsp  | Season to taste |
    And I add the recipe to the shopping list with the same idempotency key twice
    Then the shopping list contains exactly 3 items named "Tomatoes", "Olive oil" and "Salt"

  Scenario: Refresh rotates the refresh token and logout revokes access
    Given I am authenticated as "alice"
    When I refresh the session
    Then a new access token and refresh token are returned
    When I log out
    Then requesting the current user returns 401

  Scenario: Protected resources reject anonymous requests
    When I request the current user without authentication
    Then the response status is 403

  Scenario: Current user exposes local quota
    Given I am authenticated as "alice"
    When I request the current user
    Then the current user quota has limit 10 and remaining 10

  Scenario: Creating an import is idempotent and pollable
    Given I am authenticated as "alice"
    When I create the same link import twice
    Then the import is queued with one reserved quota unit
    When I request the created import
    Then the response status is 200
    And I request the created import
    And the import can be found in my import list

  Scenario: User can upload and confirm a media import
    Given I am authenticated as "upload-user"
    When I create an upload import
    And I upload and confirm the import object
    Then the upload import is queued

  @b23
  Scenario: Full mock import pipeline persists a video recipe and shopping list
    Given I am authenticated as "asr-user"
    When I create a B23 video import
    And I upload the B23 video fixture and confirm the import
    Then the import is finalized, saved and added to shopping
