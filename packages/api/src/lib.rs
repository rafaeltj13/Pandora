use axum::{Json, Router, routing::get};
use serde::Serialize;
use utoipa::{OpenApi, ToSchema};

#[derive(Serialize, ToSchema)]
pub struct HealthResponse {
    pub status: &'static str,
}

#[derive(Serialize, ToSchema)]
pub struct StatusResponse {
    pub app: &'static str,
    pub message: &'static str,
    pub version: &'static str,
}

#[utoipa::path(
    get,
    path = "/health",
    responses((status = 200, description = "Server is running", body = HealthResponse))
)]
async fn health() -> Json<HealthResponse> {
    Json(HealthResponse { status: "ok" })
}

#[utoipa::path(
    get,
    path = "/api/v1/status",
    responses((status = 200, description = "Pandora welcome message", body = StatusResponse))
)]
async fn status() -> Json<StatusResponse> {
    Json(StatusResponse {
        app: "Pandora",
        message: "Welcome to Pandora",
        version: env!("CARGO_PKG_VERSION"),
    })
}

#[derive(OpenApi)]
#[openapi(
    info(
        title = "Pandora API",
        description = "Shared HTTP contract for Pandora clients."
    ),
    paths(health, status),
    components(schemas(HealthResponse, StatusResponse))
)]
struct ApiDocument;

pub fn openapi() -> utoipa::openapi::OpenApi {
    ApiDocument::openapi()
}

/// Reusable API composition; the server owns configuration and process startup.
pub fn router() -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/api/v1/status", get(status))
        .route("/openapi.json", get(|| async { Json(openapi()) }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        body::{Body, to_bytes},
        http::{Request, StatusCode},
    };
    use serde_json::{Value, json};
    use tower::ServiceExt;

    async fn request(path: &str) -> (StatusCode, Value) {
        let response = router()
            .oneshot(Request::builder().uri(path).body(Body::empty()).unwrap())
            .await
            .unwrap();
        let status = response.status();
        assert_eq!(response.headers()["content-type"], "application/json");
        let body = to_bytes(response.into_body(), 64 * 1024).await.unwrap();
        (status, serde_json::from_slice(&body).unwrap())
    }

    #[tokio::test]
    async fn welcome_response_matches_the_client_contract() {
        let (status, body) = request("/api/v1/status").await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(
            body,
            json!({
                "app": "Pandora",
                "message": "Welcome to Pandora",
                "version": env!("CARGO_PKG_VERSION"),
            })
        );
    }

    #[tokio::test]
    async fn health_is_available_independently_of_the_app_screen() {
        assert_eq!(
            request("/health").await,
            (StatusCode::OK, json!({"status": "ok"}))
        );
    }

    #[tokio::test]
    async fn published_contract_describes_the_live_routes() {
        let (status, document) = request("/openapi.json").await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(document, serde_json::to_value(openapi()).unwrap());
        for route in ["/health", "/api/v1/status"] {
            assert_eq!(request(route).await.0, StatusCode::OK);
            assert!(document["paths"][route]["get"]["responses"]["200"].is_object());
        }
    }
}
