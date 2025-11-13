# monumentos-proxy

Pequena API Spring Boot para enriquecer POIs culturais do projeto `.pt`
com dados do site **monumentos.gov.pt**.

> **Aviso importante**
>
> - Os selectores de HTML no `MonumentService` são genéricos/"best effort".
>   Depois de correres a app, convém inspecionar o HTML real de algumas páginas
>   de monumentos.gov.pt e ajustar:
>   - o URL de pesquisa/detalhe;
>   - os `doc.select(...)` usados para nome, descrições, localização, etc.
>
> - Usa _cache in-memory_ (Caffeine) para não abusar do site.

## Como correr

```bash
mvn spring-boot:run
```

Ou construir jar:

```bash
mvn clean package
java -jar target/monumentos-proxy-0.0.1-SNAPSHOT.jar
```

## Endpoints

- `GET /api/monuments/search?name=Mosteiro dos Jerónimos`

  Retorna uma lista de `MonumentDto` com campos:
  - `id`, `slug`
  - `originalName`, `normalizedName`
  - `locality`, `district`, `concelho`, `freguesia`
  - `shortDescription`
  - `sourceUrl`
  - (eventualmente lat/lon, imagens, etc. se forem detectados na página).

- `GET /api/monuments/{slug}`

  Carrega o detalhe de um monumento específico, com HTML mais completo
  e lista de imagens se disponível.

## Integração no Frontend

Do lado do `.pt` React / Vite:

```ts
const res = await fetch("http://localhost:8085/api/monuments/search?name=" + encodeURIComponent(nomePoi));
const list = await res.json() as MonumentDto[];
```

Depois decides no FE:
- se mostras primeiro descrição de monumentos.gov, fallback para Wikipedia,
- como misturar as fotos,
- etc.
