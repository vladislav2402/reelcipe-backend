package com.reelcipe.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ReelcipeApiSteps {
    private final List<String> createdRecipeIds = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestClient client;
    private AuthClient authClient;
    private RecipeClient recipeClient;
    private ShoppingClient shoppingClient;
    private ImportClient importClient;
    private String accessToken;
    private String refreshToken;
    private String recipeId;
    private String recipeVersion;
    private String importId;
    private UUID uploadAttemptId;
    private String uploadUrl;
    private String idempotencyKey;
    private byte[] asrFixture;
    private Path asrFixturePath;
    private Response lastResponse;

    @Before
    public void resetScenarioState() {
        accessToken = null;
        refreshToken = null;
        recipeId = null;
        recipeVersion = null;
        importId = null;
        uploadAttemptId = null;
        uploadUrl = null;
        idempotencyKey = null;
        asrFixture = null;
        asrFixturePath = null;
        lastResponse = null;
        createdRecipeIds.clear();
        String baseUrl = System.getProperty(
                "e2e.base-url",
                System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8080"));
        client = RestClient.builder().baseUrl(baseUrl).build();
        authClient = new AuthClient(client, objectMapper);
        recipeClient = new RecipeClient(client, objectMapper);
        shoppingClient = new ShoppingClient(client, objectMapper);
        importClient = new ImportClient(client, objectMapper);
    }

    @After
    public void cleanupScenarioData() {
        try {
            if (accessToken == null) {
                return;
            }
            for (String createdRecipeId : createdRecipeIds) {
                RecipeClient.Response recipe = recipeClient.getRecipe(createdRecipeId);
                if (recipe.status() == 200) {
                    JsonNode recipeBody = json(recipe.body());
                    recipeClient.deleteRecipe(
                            createdRecipeId,
                            recipeBody.get("version").asText(),
                            UUID.randomUUID().toString());
                }
            }

            ShoppingClient.Response shoppingListResponse = shoppingClient.getShoppingList();
            Response shoppingList = new Response(
                    shoppingListResponse.status(), shoppingListResponse.body(), shoppingListResponse.etag());
            if (shoppingList.status() == 200 && shoppingList.body() != null) {
                JsonNode items = json(shoppingList.body()).get("items");
                if (items != null) {
                    for (JsonNode item : items) {
                        shoppingClient.deleteItem(
                                item.get("id").asText(),
                                item.get("version").asText(),
                                UUID.randomUUID().toString());
                    }
                }
            }
        } catch (Exception ignored) {
            // Cleanup must not hide the original scenario failure.
        } finally {
            deleteFixture();
        }
    }

    @Given("I am authenticated as {string}")
    public void authenticate(String fixture) {
        String randomUsername = "e2e-" + fixture + "-" + UUID.randomUUID();
        AuthClient.Response authResponse = authClient.devLogin(randomUsername);
        Response response = new Response(authResponse.status(), authResponse.body(), null);
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response.body());
        accessToken = body.get("accessToken").asText();
        refreshToken = body.get("refreshToken").asText();
        authClient.setAccessToken(accessToken);
        recipeClient.setAccessToken(accessToken);
        shoppingClient.setAccessToken(accessToken);
        importClient.setAccessToken(accessToken);
    }

    @When("I create a recipe with:")
    public void createRecipe(DataTable dataTable) {
        idempotencyKey = UUID.randomUUID().toString();
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        assertThat(rows).hasSizeGreaterThanOrEqualTo(3);

        RecipeRow firstRow = RecipeRow.from(rows.getFirst());
        RecipeClient.RecipePayload recipe = new RecipeClient.RecipePayload(
                firstRow.title(),
                firstRow.language(),
                rows.stream().map(RecipeRow::from).map(RecipeRow::ingredient).toArray(RecipeClient.IngredientPayload[]::new),
                rows.stream().map(RecipeRow::from).map(RecipeRow::step).toArray(RecipeClient.StepPayload[]::new));
        RecipeClient.Response response = recipeClient.createRecipe(recipe, idempotencyKey);
        lastResponse = new Response(response.status(), response.body(), response.etag());
        assertThat(lastResponse.status()).isEqualTo(201);
        JsonNode body = json(lastResponse.body());
        recipeId = body.get("id").asText();
        recipeVersion = body.get("version").asText();
        createdRecipeIds.add(recipeId);
    }

    @Then("the recipe is created with version {int}")
    public void recipeCreated(int version) {
        assertThat(recipeVersion).isEqualTo(String.valueOf(version));
        assertThat(lastResponse.header("ETag")).isEqualTo("\"" + version + "\"");
    }

    @And("the recipe response contains ingredient {string}")
    public void containsIngredient(String name) {
        assertThat(json(lastResponse.body()).at("/ingredients/0/name").asText()).isEqualTo(name);
    }

    @When("I update the recipe title to {string}")
    public void updateRecipe(String title) {
        RecipeClient.RecipePayload recipe = new RecipeClient.RecipePayload(
                title, "EN", new RecipeClient.IngredientPayload[0], new RecipeClient.StepPayload[0]);
        RecipeClient.Response response = recipeClient.updateRecipe(
                recipeId, recipeVersion, recipe, UUID.randomUUID().toString());
        lastResponse = new Response(response.status(), response.body(), response.etag());
        assertThat(lastResponse.status()).isEqualTo(200);
        recipeVersion = json(lastResponse.body()).get("version").asText();
    }

    @Then("the recipe is updated with version {int}")
    public void recipeUpdated(int version) {
        assertThat(recipeVersion).isEqualTo(String.valueOf(version));
    }

    @When("I request the recipe")
    public void requestRecipe() {
        RecipeClient.Response response = recipeClient.getRecipe(recipeId);
        lastResponse = new Response(response.status(), response.body(), response.etag());
    }

    @Then("the recipe title is {string}")
    public void recipeTitle(String title) {
        assertThat(json(lastResponse.body()).get("title").asText()).isEqualTo(title);
    }

    @When("I delete the recipe")
    public void deleteRecipe() {
        RecipeClient.Response response = recipeClient.deleteRecipe(
                recipeId, recipeVersion, UUID.randomUUID().toString());
        lastResponse = new Response(response.status(), response.body(), response.etag());
        assertThat(lastResponse.status()).isEqualTo(204);
    }

    @Then("the recipe is deleted")
    public void recipeDeleted() {
        assertThat(lastResponse.status()).isEqualTo(204);
    }

    @And("requesting the deleted recipe returns 404")
    public void deletedRecipeNotFound() {
        RecipeClient.Response response = recipeClient.getRecipe(recipeId);
        lastResponse = new Response(response.status(), response.body(), response.etag());
        assertThat(lastResponse.status()).isEqualTo(404);
    }

    @When("I add the recipe to the shopping list with the same idempotency key twice")
    public void addRecipeTwice() {
        idempotencyKey = UUID.randomUUID().toString();
        ShoppingClient.RecipeAdditionPayload addition = new ShoppingClient.RecipeAdditionPayload(
                recipeId, 1, UUID.randomUUID());
        ShoppingClient.Response firstResponse = shoppingClient.addRecipe(addition, idempotencyKey);
        ShoppingClient.Response secondResponse = shoppingClient.addRecipe(addition, idempotencyKey);
        Response first = new Response(firstResponse.status(), firstResponse.body(), firstResponse.etag());
        Response second = new Response(secondResponse.status(), secondResponse.body(), secondResponse.etag());
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        lastResponse = second;
    }

    @Then("the shopping list contains exactly {int} items named {string}, {string} and {string}")
    public void shoppingListContainsItems(int expectedCount, String firstName, String secondName, String thirdName) {
        JsonNode items = json(lastResponse.body()).get("items");
        assertThat(items).hasSize(expectedCount);
        assertThat(items.get(0).get("name").asText()).isEqualTo(firstName);
        assertThat(items.get(1).get("name").asText()).isEqualTo(secondName);
        assertThat(items.get(2).get("name").asText()).isEqualTo(thirdName);
    }

    @When("I refresh the session")
    public void refresh() {
        AuthClient.Response authResponse = authClient.refresh(refreshToken);
        lastResponse = new Response(authResponse.status(), authResponse.body(), null);
    }

    @Then("a new access token and refresh token are returned")
    public void newTokens() {
        assertThat(lastResponse.status()).isEqualTo(200);
        JsonNode body = json(lastResponse.body());
        String oldRefresh = refreshToken;
        accessToken = body.get("accessToken").asText();
        refreshToken = body.get("refreshToken").asText();
        authClient.setAccessToken(accessToken);
        assertThat(refreshToken).isNotEqualTo(oldRefresh);
    }

    @When("I log out")
    public void logout() {
        AuthClient.Response authResponse = authClient.logout();
        lastResponse = new Response(authResponse.status(), authResponse.body(), null);
    }

    @Then("requesting the current user returns 401")
    public void currentUserUnauthorized() {
        AuthClient.Response authResponse = authClient.getCurrentUser();
        lastResponse = new Response(authResponse.status(), authResponse.body(), null);
        assertThat(lastResponse.status()).isEqualTo(401);
    }

    @When("I request the current user without authentication")
    public void anonymousMe() {
        lastResponse = exchange("GET", "/v1/me", null, false, null);
    }

    @When("I request the current user")
    public void currentUser() {
        AuthClient.Response response = authClient.getCurrentUser();
        lastResponse = new Response(response.status(), response.body(), null);
    }

    @Then("the current user quota has limit {int} and remaining {int}")
    public void currentUserQuota(int limit, int remaining) {
        assertThat(lastResponse.status()).isEqualTo(200);
        JsonNode quota = json(lastResponse.body()).get("quota");
        assertThat(quota.get("plan").asText()).isEqualTo("FREE");
        assertThat(quota.get("limit").asInt()).isEqualTo(limit);
        assertThat(quota.get("remaining").asInt()).isEqualTo(remaining);
    }

    @When("I create the same link import twice")
    public void createLinkImportTwice() {
        UUID clientRequestId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();
        ImportClient.Response first = importClient.createLink(
                clientRequestId, "https://example.com/e2e-video", idempotencyKey);
        ImportClient.Response second = importClient.createLink(
                clientRequestId, "https://example.com/e2e-video", idempotencyKey);
        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(202);
        assertThat(json(first.body()).get("id").asText())
                .isEqualTo(json(second.body()).get("id").asText());
        importId = json(second.body()).get("id").asText();
        lastResponse = new Response(second.status(), second.body(), null);
    }

    @When("I create an upload import")
    public void createUploadImport() {
        long sizeBytes = 12;
        ImportClient.Response response = importClient.createUpload(
                UUID.randomUUID(),
                "e2e-video.mp4",
                "video/mp4",
                sizeBytes,
                UUID.randomUUID().toString());
        lastResponse = new Response(response.status(), response.body(), null);
        assertThat(lastResponse.status()).isEqualTo(202);
        JsonNode body = json(lastResponse.body());
        importId = body.get("id").asText();
        assertThat(body.get("status").asText()).isEqualTo("AWAITING_UPLOAD");
    }

    @When("I upload and confirm the import object")
    public void uploadAndConfirmImportObject() {
        ImportClient.Response urlResponse = importClient.uploadUrl(importId);
        assertThat(urlResponse.status()).isEqualTo(200);
        JsonNode urlBody = json(urlResponse.body());
        uploadAttemptId = UUID.fromString(urlBody.get("uploadAttemptId").asText());
        uploadUrl = urlBody.get("url").asText();

        byte[] content = "e2e-upload!!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).hasSize(12);
        ImportClient.Response uploadResponse = importClient.putObject(
                uploadUrl, content, "video/mp4");
        assertThat(uploadResponse.status()).isIn(200, 201, 204);

        ImportClient.Response completeResponse = importClient.completeUpload(
                importId, uploadAttemptId);
        lastResponse = new Response(completeResponse.status(), completeResponse.body(), null);
    }

    @Then("the upload import is queued")
    public void uploadImportQueued() {
        assertThat(lastResponse.status()).isEqualTo(200);
        assertThat(json(lastResponse.body()).get("status").asText()).isEqualTo("QUEUED");
    }

    @When("I create an ASR audio import")
    public void createAsrAudioImport() {
        asrFixture = createAsrFixture();
        ImportClient.Response response = importClient.createUpload(
                UUID.randomUUID(),
                "AUDIO",
                "b20-e2e.flac",
                "audio/flac",
                asrFixture.length,
                UUID.randomUUID().toString());
        lastResponse = new Response(response.status(), response.body(), null);
        assertThat(lastResponse.status()).isEqualTo(202);
        JsonNode body = json(lastResponse.body());
        importId = body.get("id").asText();
        assertThat(body.get("status").asText()).isEqualTo("AWAITING_UPLOAD");
    }

    @And("I upload the ASR fixture and confirm the import")
    public void uploadAsrFixtureAndConfirmImport() {
        ImportClient.Response urlResponse = importClient.uploadUrl(importId);
        assertThat(urlResponse.status()).isEqualTo(200);
        JsonNode urlBody = json(urlResponse.body());
        uploadAttemptId = UUID.fromString(urlBody.get("uploadAttemptId").asText());
        uploadUrl = urlBody.get("url").asText();
        ImportClient.Response uploadResponse = importClient.putObject(
                uploadUrl,
                asrFixture,
                "audio/flac");
        assertThat(uploadResponse.status()).isIn(200, 201, 204);
        ImportClient.Response completeResponse = importClient.completeUpload(
                importId,
                uploadAttemptId);
        lastResponse = new Response(completeResponse.status(), completeResponse.body(), null);
        assertThat(lastResponse.status()).isEqualTo(200);
        assertThat(json(lastResponse.body()).get("status").asText()).isEqualTo("QUEUED");
    }

    @Then("the import reaches the transcription checkpoint")
    public void importReachesTranscriptionCheckpoint() {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        String status = null;
        while (System.nanoTime() < deadline) {
            ImportClient.Response response = importClient.get(importId);
            lastResponse = new Response(response.status(), response.body(), null);
            assertThat(lastResponse.status()).isEqualTo(200);
            status = json(lastResponse.body()).get("status").asText();
            if ("EXTRACTING_RECIPE".equals(status)) {
                break;
            }
            if ("FAILED".equals(status) || "CANCELLED".equals(status)
                    || "EXPIRED".equals(status)) {
                break;
            }
            sleep(Duration.ofMillis(500));
        }
        assertThat(status).isEqualTo("EXTRACTING_RECIPE");
    }

    @Then("the import is queued with one reserved quota unit")
    public void importQueuedWithQuota() {
        JsonNode body = json(lastResponse.body());
        assertThat(lastResponse.status()).isEqualTo(202);
        assertThat(body.get("status").asText()).isEqualTo("QUEUED");
        assertThat(body.get("pollAfterSeconds").asInt()).isEqualTo(2);
        assertThat(body.at("/quota/reserved").asInt()).isEqualTo(1);
        assertThat(body.at("/quota/remaining").asInt()).isEqualTo(9);
    }

    @When("I request the created import")
    public void requestImport() {
        ImportClient.Response response = importClient.get(importId);
        lastResponse = new Response(response.status(), response.body(), null);
    }

    @Then("the import can be found in my import list")
    public void importInList() {
        ImportClient.Response response = importClient.list();
        assertThat(response.status()).isEqualTo(200);
        assertThat(json(response.body()).toString()).contains(importId);
    }

    @Then("the response status is {int}")
    public void responseStatus(int status) {
        assertThat(lastResponse.status()).isEqualTo(status);
    }

    private Response exchange(String method, String path, String body, boolean authenticated, String key) {
        return exchange(method, path, body, authenticated, key, null);
    }

    private Response exchange(String method, String path, String body, boolean authenticated, String key, String ifMatch) {
        try {
            RestClient.RequestBodySpec request = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
            request.contentType(MediaType.APPLICATION_JSON);
            if (authenticated) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            }
            if (key != null) {
                request.header("Idempotency-Key", key);
            }
            if (ifMatch != null) {
                request.header(HttpHeaders.IF_MATCH, ifMatch);
            }
            ResponseEntity<String> response = request.body(body == null ? "" : body).retrieve().toEntity(String.class);
            return new Response(response.getStatusCode().value(), response.getBody(), response.getHeaders().getETag());
        } catch (RestClientResponseException e) {
            assert e.getResponseHeaders() != null;
            return new Response(e.getStatusCode().value(), e.getResponseBodyAsString(), e.getResponseHeaders().getFirst("ETag"));
        }
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Invalid JSON response: " + body, e);
        }
    }

    private byte[] createAsrFixture() {
        Path workDirectory = Path.of(System.getProperty("user.dir"), ".local", "media-work");
        asrFixturePath = workDirectory.resolve("b20-e2e-source.flac");
        try {
            Files.createDirectories(workDirectory);
            List<String> command = List.of(
                    "docker",
                    "compose",
                    "--env-file",
                    System.getenv().getOrDefault("E2E_COMPOSE_ENV_FILE", "infra/.env.local"),
                    "-f",
                    System.getenv().getOrDefault(
                            "E2E_COMPOSE_FILE", "infra/compose.local.yml"),
                    "exec",
                    "-T",
                    "media-tools",
                    "ffmpeg",
                    "-v",
                    "error",
                    "-nostdin",
                    "-y",
                    "-f",
                    "lavfi",
                    "-i",
                    "sine=frequency=440:sample_rate=16000",
                    "-t",
                    "4",
                    "-metadata",
                    "comment=reelcipe-b20-fixture:recipe-audio-v1",
                    "-c:a",
                    "flac",
                    "/work/b20-e2e-source.flac");
            Process process = new ProcessBuilder(command)
                    .directory(Path.of(System.getProperty("user.dir")).toFile())
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Timed out while creating ASR fixture");
            }
            String output = new String(
                    process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue())
                    .withFailMessage("Unable to create ASR fixture: %s", output)
                    .isZero();
            return Files.readAllBytes(asrFixturePath);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Unable to create ASR fixture", exception);
        } catch (IOException exception) {
            throw new AssertionError("Unable to create ASR fixture", exception);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("E2E polling was interrupted", exception);
        }
    }

    private void deleteFixture() {
        if (asrFixturePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(asrFixturePath);
        } catch (IOException ignored) {
            // E2E fixture cleanup must not hide a scenario result.
        }
    }

    private record RecipeRow(String title, String language, RecipeClient.IngredientPayload ingredient,
                             RecipeClient.StepPayload step) {
        private static RecipeRow from(Map<String, String> row) {
            return new RecipeRow(
                    row.get("title"),
                    row.get("language"),
                    new RecipeClient.IngredientPayload(
                            row.get("ingredient"),
                            new BigDecimal(row.get("amount")),
                            row.get("unit")),
                    new RecipeClient.StepPayload(row.get("step")));
        }
    }

    private record Response(int status, String body, String etag) {
        String header(String name) {
            return "ETag".equalsIgnoreCase(name) ? etag : null;
        }
    }
}
