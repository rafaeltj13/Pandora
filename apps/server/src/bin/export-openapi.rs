fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("{}", pandora_api::openapi().to_pretty_json()?);
    Ok(())
}
