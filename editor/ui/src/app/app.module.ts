import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ToastrModule } from 'ngx-toastr';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { AppComponent } from './app.component';
import { WelcomeComponent } from './welcome/welcome.component';
import { LoginComponent } from './login/login.component';
import { UsersOverviewComponent } from './users-overview/users-overview.component';
import { SourcesOverviewComponent } from './sources-overview/sources-overview.component';
import { SourceComponent } from './source/source.component';
import { DocumentComponent } from './document/document.component';
import { DocumentQueryComponent } from './document-query/document-query.component';
import { SourceQueryComponent } from './source-query/source-query.component';
import { NotesComponent } from './notes/notes.component';
import { RootSectionComponent } from './section/root-section.component';
import { FormteilSectionComponent } from './section/formteil-section.component';
import { MiscSectionComponent } from './section/misc-section.component';
import { ZeileSectionComponent } from './section/zeile-section.component';
import { ParatextSectionComponent } from './section/paratext-section.component';
import { DraggerComponent } from './dragger/dragger.component';
import { LineChangeComponent } from './line-change/line-change.component';
import { FolioChangeComponent } from './folio-change/folio-change.component';
import { SmartTableComponent } from './smart-table/smart-table.component';
import { SselectComponent } from './sselect/sselect.component';
import { ClefComponent } from './clef/clef.component';
import { BoxComponent } from './box/box.component';

import { ConfirmDeactivateGuard } from './ConfirmDeactivateGuard';
import { ZipUploadComponent } from './zip-upload/zip-upload.component';
import { EditSyllableTextComponent } from './edit-syllable-text/edit-syllable-text.component';
import { CommentComponent } from './comment/comment.component';
import { ComplexCommentComponent } from './complex-comment/complex-comment.component';
import { ParatextCommentComponent } from './paratext-comment/paratext-comment.component';
import { CommentTreeComponent } from './complex-comment/comment-tree/comment-tree.component';
import { CommentTreeLeafComponent } from './complex-comment/comment-tree/comment-tree-leaf/comment-tree-leaf.component';
import { CommentTreeUndecidedComponent } from './complex-comment/comment-tree/comment-tree-undecided/comment-tree-undecided.component';
import { CommentTreeGridComponent } from './complex-comment/comment-tree/comment-tree-grid/comment-tree-grid.component';
import { CommentTreeBracketComponent } from './complex-comment/comment-tree/comment-tree-leaf/comment-tree-bracket/comment-tree-bracket.component';
import { CommentTreeTextComponent } from './complex-comment/comment-tree/comment-tree-leaf/comment-tree-text/comment-tree-text.component';
import { CommentTreeNotesComponent } from './complex-comment/comment-tree/comment-tree-leaf/comment-tree-notes/comment-tree-notes.component';
import { CommentTreeActionDotComponent } from './complex-comment/comment-tree/comment-tree-action-dot/comment-tree-action-dot.component';
import { CommentInfoComponent } from './comment/comment-info/comment-info.component';

@NgModule({
  declarations: [
    AppComponent,
    WelcomeComponent,
    LoginComponent,
    UsersOverviewComponent,
    SourcesOverviewComponent,
    SourceComponent,
    DocumentComponent,
    DocumentQueryComponent,
    SourceQueryComponent,
    NotesComponent,
    RootSectionComponent,
    FormteilSectionComponent,
    MiscSectionComponent,
    ZeileSectionComponent,
    ParatextSectionComponent,
    DraggerComponent,
    LineChangeComponent,
    FolioChangeComponent,
    SmartTableComponent,
    SselectComponent,
    ClefComponent,
    BoxComponent,
    ZipUploadComponent,
    EditSyllableTextComponent,
    CommentComponent,
    ParatextCommentComponent,
    ComplexCommentComponent,
    CommentTreeComponent,
    CommentTreeLeafComponent,
    CommentTreeUndecidedComponent,
    CommentTreeGridComponent,
    CommentTreeBracketComponent,
    CommentTreeTextComponent,
    CommentTreeNotesComponent,
    CommentTreeActionDotComponent,
    CommentInfoComponent
  ],
  imports: [
    BrowserModule,
    CommonModule,
    BrowserAnimationsModule,
    ToastrModule.forRoot(),
    FormsModule,
    HttpClientModule,
    NgbModule,
    RouterModule.forRoot([
      {
        path: 'login',
        component: LoginComponent,
      }, {
        path: 'users',
        component: UsersOverviewComponent,
      }, {
        path: 'sources',
        component: SourcesOverviewComponent,
      }, {
        path: 'source/:id',
        component: SourceComponent,
      }, {
        path: 'source',
        component: SourceComponent,
      }, {
        path: 'document/:source',
        component: DocumentComponent,
      }, {
        path: 'document/:source/:id',
        component: DocumentComponent,
        canDeactivate: [ConfirmDeactivateGuard]
      }, {
        path: 'search',
        component: DocumentQueryComponent,
      },
      {
        path: 'search/:query',
        component: DocumentQueryComponent,
      }, {
        path: 'search-source',
        component: SourceQueryComponent,
      },
      {
        path: 'search-source/:query',
        component: SourceQueryComponent,
      }, {
        path: 'zip-upload',
        component: ZipUploadComponent,
      }, {
        path: 'cc',
        component: ComplexCommentComponent
      }, {
        path: '**',
        component: WelcomeComponent
      }
    ], { useHash: true })
  ],
  providers: [ConfirmDeactivateGuard],
  bootstrap: [AppComponent]
})
export class AppModule { }
