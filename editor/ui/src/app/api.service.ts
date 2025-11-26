import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../environments/environment';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import * as VM from './types/model';

@Injectable({
  providedIn: 'root'
})
export class APIService {

  constructor(private http: HttpClient) { }

  public logout(): Observable<null> {
    return this.doPost<null, null>("login/logout", null).pipe(map(s => null));
  }

  public login(user: string, password: string): Observable<InvalidUsernameFormat | LoginFailed | LoginSuccessful> {
    return this.doPost<any, any>("login/login", { user: user, password: password });
  }

  public listUsers(token: string): Observable<LoginRequired | InsufficientPermissions | UserInfosRetrieved> {
    return this.doPostWithToken<any, any>(token, "user/list", {});
  }

  public createUser(token: string, user: string, password: string): Observable<LoginRequired | InsufficientPermissions | Ok | InvalidUsernameFormat | UserAlreadyExists> {
    return this.doPostWithToken<any, any>(token, "user/create", { user: user, password: password });
  }

  public removeUser(token: string, user: string): Observable<LoginRequired | InsufficientPermissions | Ok | InvalidUsernameFormat | TriedToRemoveSelf> {
    return this.doPostWithToken<any, any>(token, "user/remove", JSON.stringify(user));
  }

  public listSources(token: string): Observable<LoginRequired | SourcesRetrieved> {
    return this.doPostWithToken<any, any>(token, "source/list", {});
  }

  public updateSource(token: string, source: Source): Observable<LoginRequired | Ok | SourceNotFound> {
    return this.doPostWithToken<any, any>(token, "source/update", source);
  }

  public getSource(token: string, id: string): Observable<LoginRequired | SourceRetrieved | SourceNotFound | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "source/get", JSON.stringify(id));
  }

  public getSigle(token: string, id: string): Observable<LoginRequired | SigleRetrieved | SourceNotFound> {
    return this.doPostWithToken<any, any>(token, "source/getSigle", JSON.stringify(id));
  }


  public querySources(token: string, query: SourceQuery): Observable<LoginRequired | SourcesRetrieved> {
    return this.doPostWithToken<any, any>(token, "source/query", query);
  }


  public createSource(token: string, source: Source): Observable<LoginRequired | SourceCreated> {
    return this.doPostWithToken<any, any>(token, "source/create", source);
  }

  public importZip(token: string, data: string): Observable<LoginRequired | UploadFinished | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "source/import", JSON.stringify(data));
  }

  public importDocuments(token: string, data: string): Observable<LoginRequired | UploadFinished | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "source/importDocuments", JSON.stringify(data));
  }

  public importSources(token: string, data: string): Observable<LoginRequired | UploadFinished | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "source/importSources", JSON.stringify(data));
  }

  public deleteDocuments(token: string, data: string): Observable<LoginRequired | UploadFinished | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "source/deleteDocuments", JSON.stringify(data));
  }

  public deleteSources(token: string, data: string): Observable<LoginRequired | UploadFinished | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "source/deleteSources", JSON.stringify(data));
  }

  public listDocuments(token: string): Observable<LoginRequired | DocumentsRetrieved> {
    return this.doPostWithToken<any, any>(token, "document/list", {});
  }

  public updateDocument(token: string, update: CreateDocument): Observable<LoginRequired | Ok | DocumentNotFound | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "document/update", update);
  }

  public getDocument(token: string, id: string): Observable<LoginRequired | DocumentRetrieved | DocumentNotFound | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "document/get", JSON.stringify(id));
  }

  public removeDocument(token: string, id: number): Observable<LoginRequired | Ok> {
    return this.doPostWithToken<any, any>(token, "document/remove", id);
  }

  public createDocument(token: string, creation: CreateDocument): Observable<LoginRequired | DocumentCreated> {
    return this.doPostWithToken<any, any>(token, "document/create", creation);
  }

  public queryDocuments(token: string, query: DocumentQuery): Observable<LoginRequired | DocumentsRetrieved> {
    return this.doPostWithToken<any, any>(token, "document/query", query);
  }

  public getDocumentNotes(token: string, id: string): Observable<LoginRequired | NotesRetrieved | DocumentNotFound | InsufficientPermissions> {
    return this.doPostWithToken<any, any>(token, "document/getNotes", JSON.stringify(id));
  }

  public verifyNotes(token: string, notes: string): Observable<LoginRequired | NotesRetrieved | Failed> {
    return this.doPostWithToken<any, any>(token, "document/verifyNotes", JSON.stringify(notes));
  }


  private doPostWithToken<I, O>(token: string, resource: string, i: I): Observable<O> {
    const headers = new HttpHeaders()
      .append('Authorization', token);

    return this.http.post(environment.apiPrefix + resource, i, { headers: headers }) as Observable<O>;
  }

  private doPost<I, O>(resource: string, i: I): Observable<O> {
    return this.http.post(environment.apiPrefix + resource, i) as Observable<O>;
  }
}


export type User = string
export type Role = string

export interface Ok {
  "kind": "Ok";
}

export interface Failed {
  "kind": "Failed";
}

export interface LoginRequired {
  "kind": "LoginRequired";
}

export interface InsufficientPermissions {
  "kind": "InsufficientPermissions";
}

export interface InvalidUsernameFormat {
  "kind": "InvalidUsernameFormat";
}

export interface LoginFailed {
  "kind": "LoginFailed";
}

export interface LoginSuccessful {
  "kind": "LoginSuccessful";
  "user": User;
  "roles": Role[];
  "token": string;
}

export interface UserAlreadyExists {
  "kind": "UserAlreadyExists";
}

export interface UserInfosRetrieved {
  "kind": "UserInfosRetrieved";
  "infos": UserInfo[];
}

export interface TriedToRemoveSelf {
  "kind": "TriedToRemoveSelf";
}

export interface SourcesRetrieved {
  "kind": "SourcesRetrieved";
  "sources": Source[];
}

export interface SourceNotFound {
  "kind": "SourceNotFound";
}

export interface SourceRetrieved {
  "kind": "SourceRetrieved";
  "source": Source;
}

export interface SigleRetrieved {
  "kind": "SigleRetrieved";
  "sigle": string;
}

export interface SourceCreated {
  "kind": "SourceCreated";
  "id": string;
}

export interface DocumentsRetrieved {
  "kind": "DocumentsRetrieved";
  "documents": Document[];
}

export interface DocumentNotFound {
  "kind": "DocumentNotFound";
}

export interface DocumentRetrieved {
  "kind": "DocumentRetrieved";
  "document": Document;
}

export interface DocumentCreated {
  "kind": "DocumentCreated";
  "id": string;
}

export interface UploadFinished {
  "kind": "UploadFinished";
  errors: string[];
}

export interface NotesRetrieved {
  "kind": "NotesRetrieved";
  "data": VM.RootContainer;
}

export interface Source {
  id?: string;
  quellensigle: string;
  herkunftsregion: string;
  herkunftsort: string;
  herkunftsinstitution: string;
  ordenstradition: string;
  quellentyp: string;
  bibliotheksort: string;
  bibliothek: string;
  bibliothekssignatur: string;
  kommentar: string;
  datierung: string;
}

export interface Document {
  "id": string;
  "quelle_id": string;
  "dokumenten_id": string;
  "gattung1": string;
  "gattung2": string;
  "festtag": string;
  "feier": string;
  "textinitium": string;
  "bibliographischerverweis": string;
  "druckausgabe": string;
  "zeilenstart": string;
  "foliostart": string;
  "kommentar": string;
  "editionsstatus": string;
}

export interface UserInfo {
  "user": User;
  "roles": Role[];
}

export interface DocumentQuery {
  "dokumenten_id": string | undefined;
  "gattung1": string | undefined;
  "gattung2": string | undefined;
  "festtag": string | undefined;
  "feier": string | undefined;
  "textinitium": string | undefined;
  "bibliographischerverweis": string | undefined;
  "druckausgabe": string | undefined;
  "zeilenstart": string | undefined;
  "foliostart": string | undefined;
  "kommentar": string | undefined;
}

export interface CreateDocument {
  "document": Document;
  "notes": VM.RootContainer;
}

export interface SaveNotes {
  "id": string;
  "notes": VM.RootContainer;
}

export interface SourceQuery {
  quellensigle?: string | undefined;
  herkunftsregion?: string | undefined;
  herkunftsort?: string | undefined;
  herkunftsinstitution?: string | undefined;
  ordenstradition?: string | undefined;
  quellentyp?: string | undefined;
  bibliotheksort?: string | undefined;
  bibliothek?: string | undefined;
  bibliothekssignatur?: string | undefined;
  kommentar?: string | undefined;
  datierung?: string | undefined;
}



